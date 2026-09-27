package com.chris64233.spectrumcoordination.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.spectrumcoordination.domain.ApplicationStatus;
import com.chris64233.spectrumcoordination.domain.ChangeRecord;
import com.chris64233.spectrumcoordination.domain.ChangeType;
import com.chris64233.spectrumcoordination.domain.FrequencyApplication;
import com.chris64233.spectrumcoordination.domain.FrequencyOccupancy;
import com.chris64233.spectrumcoordination.domain.FrequencyResource;
import com.chris64233.spectrumcoordination.domain.IdSequence;
import com.chris64233.spectrumcoordination.domain.InterferenceRuleVersion;
import com.chris64233.spectrumcoordination.domain.License;
import com.chris64233.spectrumcoordination.domain.LicenseStatus;
import com.chris64233.spectrumcoordination.domain.RuleVersionStatus;
import com.chris64233.spectrumcoordination.domain.Station;
import com.chris64233.spectrumcoordination.dto.ApplicationDto;
import com.chris64233.spectrumcoordination.dto.ApplicationRequest;
import com.chris64233.spectrumcoordination.dto.ChangeRecordDto;
import com.chris64233.spectrumcoordination.dto.ConflictDto;
import com.chris64233.spectrumcoordination.dto.DecisionResult;
import com.chris64233.spectrumcoordination.dto.FrequencyResourceRequest;
import com.chris64233.spectrumcoordination.dto.LicenseDto;
import com.chris64233.spectrumcoordination.dto.RetuneRequest;
import com.chris64233.spectrumcoordination.dto.RuleVersionRequest;
import com.chris64233.spectrumcoordination.dto.StationRequest;
import com.chris64233.spectrumcoordination.repo.ChangeRecordRepository;
import com.chris64233.spectrumcoordination.repo.FrequencyApplicationRepository;
import com.chris64233.spectrumcoordination.repo.FrequencyOccupancyRepository;
import com.chris64233.spectrumcoordination.repo.FrequencyResourceRepository;
import com.chris64233.spectrumcoordination.repo.IdSequenceRepository;
import com.chris64233.spectrumcoordination.repo.InterferenceRuleVersionRepository;
import com.chris64233.spectrumcoordination.repo.LicenseRepository;
import com.chris64233.spectrumcoordination.repo.StationRepository;

/**
 * 频谱协调核心服务。
 *
 * <p>并发安全：申请批准与改频在事务内先对目标频率资源行加悲观写锁
 * （{@code FrequencyResourceRepository#lockById}），使同一资源上的冲突裁决全局串行，
 * 从而保证“相邻区域同频段并发申请”最终不会超过同频使用上限。
 *
 * <p>原子性：批准 / 改频 / 终止均为单一事务——许可与占用同时成功或同时回滚；
 * 改频遵循“新频率完整批准后才释放旧频率”的顺序，任一步失败原许可保持有效。
 */
@Service
public class SpectrumService {

    private static final Logger log = LoggerFactory.getLogger(SpectrumService.class);
    private static final String LICENSE_SEQ = "LICENSE_NO";
    /** 裁决冲突明细中最多保留的冲突对象数量（摘要用） */
    private static final int MAX_REPORTED_CONFLICTS = 20;

    private final StationRepository stations;
    private final FrequencyResourceRepository resources;
    private final InterferenceRuleVersionRepository rules;
    private final FrequencyApplicationRepository applications;
    private final LicenseRepository licenses;
    private final FrequencyOccupancyRepository occupancies;
    private final ChangeRecordRepository changes;
    private final IdSequenceRepository sequences;
    private final ApplicationCommandService applicationCommands;
    private final Clock clock;

    public SpectrumService(StationRepository stations, FrequencyResourceRepository resources,
                           InterferenceRuleVersionRepository rules,
                           FrequencyApplicationRepository applications, LicenseRepository licenses,
                           FrequencyOccupancyRepository occupancies, ChangeRecordRepository changes,
                           IdSequenceRepository sequences, ApplicationCommandService applicationCommands,
                           Clock clock) {
        this.stations = stations;
        this.resources = resources;
        this.rules = rules;
        this.applications = applications;
        this.licenses = licenses;
        this.occupancies = occupancies;
        this.changes = changes;
        this.sequences = sequences;
        this.applicationCommands = applicationCommands;
        this.clock = clock;
    }

    // ---------------------------------------------------------------------
    // 基础数据：台站、频率资源、规则版本
    // ---------------------------------------------------------------------

    @Transactional
    public Station createStation(StationRequest req) {
        stations.findByStationCode(req.stationCode()).ifPresent(s -> {
            throw ApiException.conflict("台站编号已存在: " + req.stationCode());
        });
        return stations.save(new Station(req.stationCode(), req.name(),
                req.latitude(), req.longitude(), req.coverageRadiusKm(),
                req.transmitPowerW(), req.deviceType()));
    }

    @Transactional(readOnly = true)
    public List<Station> listStations() {
        return stations.findAll();
    }

    @Transactional(readOnly = true)
    public Station getStation(String stationCode) {
        return stations.findByStationCode(stationCode)
                .orElseThrow(() -> ApiException.notFound("台站不存在: " + stationCode));
    }

    @Transactional
    public FrequencyResource createResource(FrequencyResourceRequest req) {
        if (!(req.bandStartMhz() < req.bandEndMhz())) {
            throw ApiException.badRequest("频段起点必须小于终点（左闭右开）");
        }
        if (req.maxCoChannelUsers() < 1) {
            throw ApiException.badRequest("最大同频使用数必须 >= 1");
        }
        return resources.save(new FrequencyResource(req.bandStartMhz(), req.bandEndMhz(),
                req.regionLatitude(), req.regionLongitude(), req.regionRadiusKm(),
                req.maxCoChannelUsers()));
    }

    @Transactional(readOnly = true)
    public List<FrequencyResource> listResources() {
        return resources.findAll();
    }

    /**
     * 发布新规则版本：旧生效版本标记为 SUPERSEDED。已有许可不被追溯；
     * 待批准申请在批准时按新版本重新校验。
     */
    @Transactional
    public InterferenceRuleVersion publishRuleVersion(RuleVersionRequest req) {
        if (req.coChannelReuseDistanceKm() < 0) {
            throw ApiException.badRequest("同频复用保护距离不能为负");
        }
        int next = rules.findMaxVersionNumber() + 1;
        rules.findByStatus(RuleVersionStatus.ACTIVE).ifPresent(old -> {
            old.setStatus(RuleVersionStatus.SUPERSEDED);
        });
        return rules.save(new InterferenceRuleVersion(next, req.coChannelReuseDistanceKm(),
                req.description() == null ? "" : req.description(),
                RuleVersionStatus.ACTIVE, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<InterferenceRuleVersion> listRuleVersions() {
        return rules.findAll();
    }

    @Transactional(readOnly = true)
    public InterferenceRuleVersion currentRule() {
        return rules.findByStatus(RuleVersionStatus.ACTIVE)
                .orElseThrow(() -> ApiException.unprocessable("系统尚未发布任何干扰规则版本"));
    }

    // ---------------------------------------------------------------------
    // 申请提交（幂等）
    // ---------------------------------------------------------------------

    /**
     * 提交申请。申请号幂等：若申请号已存在（含并发提交的竞态），直接返回既有申请，不重复创建。
     */
    @Transactional
    public ApplicationDto submitApplication(ApplicationRequest req) {
        var existing = applications.findByApplicationNo(req.applicationNo());
        if (existing.isPresent()) {
            FrequencyApplication a = existing.get();
            return DtoMapper.application(a, licenseFor(a), true);
        }
        Station station = getStation(req.stationCode());
        FrequencyResource resource = resources.findById(req.resourceId())
                .orElseThrow(() -> ApiException.notFound("频率资源不存在: id=" + req.resourceId()));
        validateBandAndTime(resource, req.bandStartMhz(), req.bandEndMhz(),
                req.startTime(), req.endTime());
        InterferenceRuleVersion rule = currentRule();

        FrequencyApplication created;
        try {
            created = applicationCommands.createNew(req, station, resource, rule);
        } catch (DataIntegrityViolationException dup) {
            // 并发下唯一索引兜底：重读抢先提交的申请，保持幂等
            FrequencyApplication winner = requireApplication(req.applicationNo());
            return DtoMapper.application(winner, licenseFor(winner), true);
        }
        if (created == null) {
            FrequencyApplication winner = requireApplication(req.applicationNo());
            return DtoMapper.application(winner, licenseFor(winner), true);
        }
        log.info("申请已受理 applicationNo={} ruleVersion={}", req.applicationNo(), rule.getVersionNumber());
        return DtoMapper.application(created, null, false);
    }

    private void validateBandAndTime(FrequencyResource resource, double bandStart, double bandEnd,
                                     Instant startTime, Instant endTime) {
        if (!(bandStart < bandEnd)) {
            throw ApiException.badRequest("申请频段起点必须小于终点（左闭右开）");
        }
        if (bandStart < resource.getBandStartMhz() || bandEnd > resource.getBandEndMhz()) {
            throw ApiException.badRequest(
                    "申请频段 %.3f-%.3f MHz 超出频率资源范围 %.3f-%.3f MHz".formatted(
                            bandStart, bandEnd, resource.getBandStartMhz(), resource.getBandEndMhz()));
        }
        if (!startTime.isBefore(endTime)) {
            throw ApiException.badRequest("使用开始时间必须早于结束时间");
        }
    }

    @Transactional(readOnly = true)
    public ApplicationDto getApplication(String applicationNo) {
        FrequencyApplication a = requireApplication(applicationNo);
        return DtoMapper.application(a, licenseFor(a), false);
    }

    @Transactional(readOnly = true)
    public List<ApplicationDto> listApplications() {
        return applications.findAll().stream()
                .map(a -> DtoMapper.application(a, licenseFor(a), false))
                .toList();
    }

    private FrequencyApplication requireApplication(String applicationNo) {
        return applications.findByApplicationNo(applicationNo)
                .orElseThrow(() -> ApiException.notFound("申请不存在: " + applicationNo));
    }

    private License licenseFor(FrequencyApplication a) {
        return a.getLicenseId() == null ? null : licenses.findById(a.getLicenseId()).orElse(null);
    }

    // ---------------------------------------------------------------------
    // 申请撤销
    // ---------------------------------------------------------------------

    /** 主动撤销待批准申请；已裁决申请不可撤销。 */
    @Transactional
    public ApplicationDto cancelApplication(String applicationNo) {
        FrequencyApplication a = requireApplication(applicationNo);
        if (a.getStatus() != ApplicationStatus.PENDING) {
            throw ApiException.unprocessable("仅待批准申请可以撤销，当前状态: " + a.getStatus());
        }
        Instant now = clock.instant();
        a.setStatus(ApplicationStatus.CANCELLED);
        a.setDecidedAt(now);
        changes.save(new ChangeRecord(null, a.getApplicationNo(), ChangeType.CANCELLED,
                ApplicationStatus.PENDING.name(), ApplicationStatus.CANCELLED.name(),
                a.getSubmittedRuleVersion().getVersionNumber(), "申请人主动撤销", now));
        return DtoMapper.application(a, null, false);
    }

    // ---------------------------------------------------------------------
    // 申请裁决：规则版本重校验 + 冲突判定 + 原子批准
    // ---------------------------------------------------------------------

    /**
     * 批准前裁决。整体在单事务内完成：
     * <ol>
     *   <li>锁住申请频段所在频率资源，串行化同资源并发裁决；</li>
     *   <li>规则版本升级时对待批准申请按当前版本重新校验并留痕；</li>
     *   <li>时间、频率、干扰区域三重重叠判定 + 同频使用上限；</li>
     *   <li>通过则原子写入“整段频段 × 整段时间”占用与许可，否则拒绝。</li>
     * </ol>
     * 已裁决申请重复裁决直接返回原结果（幂等）。
     */
    @Transactional
    public DecisionResult decide(String applicationNo) {
        FrequencyApplication a = requireApplication(applicationNo);
        if (a.getStatus() != ApplicationStatus.PENDING) {
            return priorDecision(a);
        }
        FrequencyResource resource = resources.lockById(a.getResource().getId());
        Instant now = clock.instant();
        InterferenceRuleVersion current = currentRule();
        boolean revalidated = current.getVersionNumber() != a.getSubmittedRuleVersion().getVersionNumber();

        InterferenceEngine.Candidate candidate = new InterferenceEngine.Candidate(
                resource, a.getBandStartMhz(), a.getBandEndMhz(),
                a.getUseLatitude(), a.getUseLongitude(), a.getUseRadiusKm(),
                a.getStartTime(), a.getEndTime());
        List<FrequencyOccupancy> candidates = occupancies.findOverlapCandidates(
                resource.getId(), a.getBandStartMhz(), a.getBandEndMhz(),
                a.getStartTime(), a.getEndTime());
        InterferenceEngine.CheckResult result = InterferenceEngine.check(candidate, candidates, current, null);

        a.setDecisionRuleVersion(current);
        a.setDecidedAt(now);

        if (revalidated) {
            changes.save(new ChangeRecord(null, a.getApplicationNo(), ChangeType.RULE_REVALIDATED,
                    ApplicationStatus.PENDING.name(), ApplicationStatus.PENDING.name(),
                    current.getVersionNumber(),
                    "规则版本由 v%d 升级为 v%d，待批准申请按新版本重新校验".formatted(
                            a.getSubmittedRuleVersion().getVersionNumber(), current.getVersionNumber()),
                    now));
        }

        if (!result.allowed()) {
            a.setStatus(ApplicationStatus.REJECTED);
            String reason = buildRejectReason(result, current);
            a.setDecisionReason(reason);
            changes.save(new ChangeRecord(null, a.getApplicationNo(), ChangeType.REJECTED,
                    ApplicationStatus.PENDING.name(), ApplicationStatus.REJECTED.name(),
                    current.getVersionNumber(), reason, now));
            log.info("申请拒绝 applicationNo={} ruleVersion={} conflicts={}",
                    applicationNo, current.getVersionNumber(), result.interferingCount());
            return new DecisionResult(false, a.getApplicationNo(), ApplicationStatus.REJECTED,
                    ChangeType.REJECTED, a.getSubmittedRuleVersion().getVersionNumber(),
                    current.getVersionNumber(), revalidated, null, reason,
                    toConflictDtos(result), now);
        }

        License license = approve(a, resource, current, now);
        String detail = ("原子批准整段频段 %.3f-%.3f MHz，时间 %s 至 %s，许可 %s")
                .formatted(a.getBandStartMhz(), a.getBandEndMhz(),
                        a.getStartTime(), a.getEndTime(), license.getLicenseNo());
        changes.save(new ChangeRecord(license.getLicenseNo(), a.getApplicationNo(), ChangeType.APPROVED,
                ApplicationStatus.PENDING.name(), ApplicationStatus.APPROVED.name(),
                current.getVersionNumber(), detail, now));
        log.info("申请批准 applicationNo={} license={} ruleVersion={}",
                applicationNo, license.getLicenseNo(), current.getVersionNumber());
        return new DecisionResult(true, a.getApplicationNo(), ApplicationStatus.APPROVED,
                ChangeType.APPROVED, a.getSubmittedRuleVersion().getVersionNumber(),
                current.getVersionNumber(), revalidated, license.getLicenseNo(),
                detail, List.of(), now);
    }

    /** 生成许可与整段占用并回写申请，均在当前事务内提交。 */
    private License approve(FrequencyApplication a, FrequencyResource resource,
                            InterferenceRuleVersion rule, Instant now) {
        long seq = nextLicenseSequence();
        License license = new License(BusinessNumbers.licenseNo(seq), a.getStation(), resource,
                a.getBandStartMhz(), a.getBandEndMhz(),
                a.getUseLatitude(), a.getUseLongitude(), a.getUseRadiusKm(),
                a.getStartTime(), a.getEndTime(), rule.getVersionNumber(),
                a.getApplicationNo(), now);
        licenses.save(license);
        occupancies.save(new FrequencyOccupancy(license, resource,
                a.getBandStartMhz(), a.getBandEndMhz(),
                a.getUseLatitude(), a.getUseLongitude(), a.getUseRadiusKm(),
                a.getStartTime(), a.getEndTime()));
        a.setStatus(ApplicationStatus.APPROVED);
        a.setLicenseId(license.getId());
        return license;
    }

    private long nextLicenseSequence() {
        IdSequence seq = sequences.findById(LICENSE_SEQ)
                .orElseGet(() -> sequences.save(new IdSequence(LICENSE_SEQ, 1)));
        // 对序列行加锁后递增，避免编号竞争
        seq = sequences.lockByName(LICENSE_SEQ);
        long value = seq.getNextValue();
        seq.setNextValue(value + 1);
        return value;
    }

    private DecisionResult priorDecision(FrequencyApplication a) {
        License license = licenseFor(a);
        boolean approved = a.getStatus() == ApplicationStatus.APPROVED;
        ChangeType changeType = switch (a.getStatus()) {
            case APPROVED -> ChangeType.APPROVED;
            case REJECTED -> ChangeType.REJECTED;
            case CANCELLED -> ChangeType.CANCELLED;
            case PENDING -> throw new IllegalStateException("待裁决申请不应进入既有结果分支");
        };
        return new DecisionResult(approved, a.getApplicationNo(), a.getStatus(),
                changeType,
                a.getSubmittedRuleVersion().getVersionNumber(),
                a.getDecisionRuleVersion() == null
                        ? a.getSubmittedRuleVersion().getVersionNumber()
                        : a.getDecisionRuleVersion().getVersionNumber(),
                false,
                license == null ? null : license.getLicenseNo(),
                a.getDecisionReason(), List.of(), a.getDecidedAt());
    }

    private String buildRejectReason(InterferenceEngine.CheckResult result, InterferenceRuleVersion rule) {
        String head = "按规则 v%d 判定：同频干扰对象 %d 个，达到最大同频使用数 %d".formatted(
                rule.getVersionNumber(), result.interferingCount(), result.maxCoChannelUsers());
        String tail = result.conflicts().stream()
                .limit(5)
                .map(c -> "许可 %s（台站 %s，间距 %.2f km ≤ 要求 %.2f km）"
                        .formatted(c.licenseNo(), c.stationCode(),
                                c.distanceKm(), c.requiredSeparationKm()))
                .reduce((x, y) -> x + "；" + y)
                .orElse("");
        return tail.isEmpty() ? head : head + "；冲突对象: " + tail;
    }

    private List<ConflictDto> toConflictDtos(InterferenceEngine.CheckResult result) {
        return result.conflicts().stream().limit(MAX_REPORTED_CONFLICTS).map(DtoMapper::conflict).toList();
    }

    // ---------------------------------------------------------------------
    // 冲突预查询（不落库）
    // ---------------------------------------------------------------------

    /**
     * 对指定申请做“批准前”冲突预演，返回按当前规则版本会构成冲突的对象列表。
     */
    @Transactional(readOnly = true)
    public List<ConflictDto> previewConflicts(String applicationNo) {
        FrequencyApplication a = requireApplication(applicationNo);
        InterferenceRuleVersion current = currentRule();
        return evaluateConflicts(a.getResource(), a.getBandStartMhz(), a.getBandEndMhz(),
                a.getUseLatitude(), a.getUseLongitude(), a.getUseRadiusKm(),
                a.getStartTime(), a.getEndTime(), null, current);
    }

    /**
     * 许可维度的冲突对象查询：以许可当前的频段、时间窗和使用区域预演，
     * 排除自身占用，返回与其相互干扰的其他许可占用。
     */
    @Transactional(readOnly = true)
    public List<ConflictDto> licenseConflicts(String licenseNo) {
        License l = requireLicense(licenseNo);
        InterferenceRuleVersion rule = rules.findByVersionNumber(l.getRuleVersionNumber())
                .orElseGet(this::currentRule);
        return evaluateConflicts(l.getResource(), l.getBandStartMhz(), l.getBandEndMhz(),
                l.getUseLatitude(), l.getUseLongitude(), l.getUseRadiusKm(),
                l.getStartTime(), l.getEndTime(), l.getId(), rule);
    }

    private List<ConflictDto> evaluateConflicts(FrequencyResource resource,
                                                double bandStart, double bandEnd,
                                                double lat, double lng, double radius,
                                                Instant start, Instant end,
                                                Long excludeLicenseId, InterferenceRuleVersion rule) {
        List<FrequencyOccupancy> candidates = occupancies.findOverlapCandidates(
                resource.getId(), bandStart, bandEnd, start, end);
        InterferenceEngine.CheckResult result = InterferenceEngine.check(
                new InterferenceEngine.Candidate(resource, bandStart, bandEnd, lat, lng, radius, start, end),
                candidates, rule, excludeLicenseId);
        return toConflictDtos(result);
    }

    // ---------------------------------------------------------------------
    // 许可生命周期：改频 / 暂停 / 恢复 / 提前终止
    // ---------------------------------------------------------------------

    /**
     * 未开始许可整体改频。仅当新频率（新资源/新频段）完整批准后才释放旧频率；
     * 新频冲突等失败情况下事务回滚，原许可与原占用保持有效。
     */
    @Transactional
    public LicenseDto retune(String licenseNo, RetuneRequest req) {
        License license = requireLicense(licenseNo);
        Instant now = clock.instant();
        LicenseStatus effective = license.getStatus().effective(now, license.getStartTime(), license.getEndTime());
        if (effective != LicenseStatus.NOT_STARTED) {
            throw ApiException.unprocessable(
                    "仅未开始的许可允许整体改频，当前有效状态: " + effective);
        }
        FrequencyResource newResource = resources.lockById(req.resourceId());
        if (!(req.bandStartMhz() < req.bandEndMhz())) {
            throw ApiException.badRequest("新频段起点必须小于终点（左闭右开）");
        }
        if (req.bandStartMhz() < newResource.getBandStartMhz() || req.bandEndMhz() > newResource.getBandEndMhz()) {
            throw ApiException.badRequest("新频段超出目标频率资源范围");
        }
        InterferenceRuleVersion rule = currentRule();
        List<FrequencyOccupancy> candidates = occupancies.findOverlapCandidates(
                newResource.getId(), req.bandStartMhz(), req.bandEndMhz(),
                license.getStartTime(), license.getEndTime());
        InterferenceEngine.CheckResult result = InterferenceEngine.check(
                new InterferenceEngine.Candidate(newResource, req.bandStartMhz(), req.bandEndMhz(),
                        license.getUseLatitude(), license.getUseLongitude(), license.getUseRadiusKm(),
                        license.getStartTime(), license.getEndTime()),
                candidates, rule, license.getId());
        if (!result.allowed()) {
            throw ApiException.conflict("改频失败，新频率无法完整批准（原许可保持有效）: "
                    + buildRejectReason(result, rule));
        }

        // 冲突校验通过即代表“新频率可完整批准”。此后在同一事务内先释放旧占用、
        // 再写入新占用：任一步失败事务回滚，旧许可与旧占用保持有效。
        double oldStart = license.getBandStartMhz();
        double oldEnd = license.getBandEndMhz();
        Long oldResourceId = license.getResource().getId();
        occupancies.deleteByLicenseId(license.getId());
        occupancies.flush();

        FrequencyOccupancy newOccupancy = new FrequencyOccupancy(license, newResource,
                req.bandStartMhz(), req.bandEndMhz(),
                license.getUseLatitude(), license.getUseLongitude(), license.getUseRadiusKm(),
                license.getStartTime(), license.getEndTime());
        occupancies.save(newOccupancy);

        license.setResource(newResource);
        license.setBandStartMhz(req.bandStartMhz());
        license.setBandEndMhz(req.bandEndMhz());

        changes.save(new ChangeRecord(license.getLicenseNo(), license.getSourceApplicationNo(),
                ChangeType.RETUNED, LicenseStatus.NOT_STARTED.name(), LicenseStatus.NOT_STARTED.name(),
                rule.getVersionNumber(),
                "整体改频：资源 %d 频段 %.3f-%.3f MHz -> 资源 %d 频段 %.3f-%.3f MHz；新频完整批准后释放旧频"
                        .formatted(oldResourceId, oldStart, oldEnd,
                                newResource.getId(), req.bandStartMhz(), req.bandEndMhz()),
                now));
        log.info("许可改频成功 license={} oldBand={}-{} newBand={}-{}",
                licenseNo, oldStart, oldEnd, req.bandStartMhz(), req.bandEndMhz());
        return DtoMapper.license(license, clock);
    }

    /** 已开始许可暂停：保留占用（保障恢复），仅切换状态。 */
    @Transactional
    public LicenseDto suspend(String licenseNo) {
        License license = requireLicense(licenseNo);
        Instant now = clock.instant();
        LicenseStatus effective = license.getStatus().effective(now, license.getStartTime(), license.getEndTime());
        if (effective != LicenseStatus.ACTIVE) {
            throw ApiException.unprocessable("仅进行中的许可允许暂停，当前有效状态: " + effective);
        }
        license.setStatus(LicenseStatus.SUSPENDED);
        changes.save(new ChangeRecord(license.getLicenseNo(), license.getSourceApplicationNo(),
                ChangeType.SUSPENDED, LicenseStatus.ACTIVE.name(), LicenseStatus.SUSPENDED.name(),
                license.getRuleVersionNumber(), "许可暂停，频率占用保留", now));
        return DtoMapper.license(license, clock);
    }

    /** 暂停许可恢复。 */
    @Transactional
    public LicenseDto resume(String licenseNo) {
        License license = requireLicense(licenseNo);
        Instant now = clock.instant();
        if (license.getStatus() != LicenseStatus.SUSPENDED) {
            throw ApiException.unprocessable("仅暂停的许可允许恢复，存储状态: " + license.getStatus());
        }
        if (!now.isBefore(license.getEndTime())) {
            throw ApiException.unprocessable("许可时间窗已结束，无法恢复");
        }
        license.setStatus(LicenseStatus.ACTIVE);
        changes.save(new ChangeRecord(license.getLicenseNo(), license.getSourceApplicationNo(),
                ChangeType.RESUMED, LicenseStatus.SUSPENDED.name(), LicenseStatus.ACTIVE.name(),
                license.getRuleVersionNumber(), "许可恢复使用", now));
        return DtoMapper.license(license, clock);
    }

    /**
     * 提前终止：仅已开始（含暂停）许可可终止。把占用与许可时间窗截断到当前时刻，
     * 原始时间窗（originalStart/End）保留用于历史查询。
     */
    @Transactional
    public LicenseDto terminate(String licenseNo) {
        License license = requireLicense(licenseNo);
        Instant now = clock.instant();
        LicenseStatus effective = license.getStatus().effective(now, license.getStartTime(), license.getEndTime());
        if (effective != LicenseStatus.ACTIVE && effective != LicenseStatus.SUSPENDED) {
            throw ApiException.unprocessable("仅已开始的许可允许提前终止，当前有效状态: " + effective);
        }
        List<FrequencyOccupancy> existing = occupancies.findByLicenseId(license.getId());
        for (FrequencyOccupancy o : existing) {
            o.setEndTime(now);
        }
        license.setEndTime(now);
        license.setStatus(LicenseStatus.TERMINATED);
        changes.save(new ChangeRecord(license.getLicenseNo(), license.getSourceApplicationNo(),
                ChangeType.TERMINATED,
                effective.name(), LicenseStatus.TERMINATED.name(),
                license.getRuleVersionNumber(),
                "提前终止，占用截止 %s；原始时间窗 %s 至 %s".formatted(
                        now, license.getOriginalStartTime(), license.getOriginalEndTime()),
                now));
        log.info("许可提前终止 license={} at={}", licenseNo, now);
        return DtoMapper.license(license, clock);
    }

    // ---------------------------------------------------------------------
    // 查询：许可、变更记录
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public LicenseDto getLicense(String licenseNo) {
        return DtoMapper.license(requireLicense(licenseNo), clock);
    }

    @Transactional(readOnly = true)
    public List<LicenseDto> listLicenses() {
        return licenses.findAll().stream().map(l -> DtoMapper.license(l, clock)).toList();
    }

    @Transactional(readOnly = true)
    public List<ChangeRecordDto> changesOfLicense(String licenseNo) {
        requireLicense(licenseNo);
        return changes.findByLicenseNo(licenseNo).stream().map(DtoMapper::change).toList();
    }

    @Transactional(readOnly = true)
    public List<ChangeRecordDto> changesOfApplication(String applicationNo) {
        requireApplication(applicationNo);
        return changes.findByApplicationNo(applicationNo).stream().map(DtoMapper::change).toList();
    }

    @Transactional(readOnly = true)
    public List<ChangeRecordDto> latestChanges(int limit) {
        int safe = Math.max(1, Math.min(limit, 200));
        return changes.findLatest(PageRequest.of(0, safe)).stream().map(DtoMapper::change).toList();
    }

    private License requireLicense(String licenseNo) {
        return licenses.findByLicenseNo(licenseNo)
                .orElseThrow(() -> ApiException.notFound("许可不存在: " + licenseNo));
    }
}
