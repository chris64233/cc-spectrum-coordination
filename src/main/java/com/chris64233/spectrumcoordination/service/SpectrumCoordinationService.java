package com.chris64233.spectrumcoordination.service;

import com.chris64233.spectrumcoordination.domain.ApplicationConflict;
import com.chris64233.spectrumcoordination.domain.ApplicationStatus;
import com.chris64233.spectrumcoordination.domain.ChangeType;
import com.chris64233.spectrumcoordination.domain.FrequencyApplication;
import com.chris64233.spectrumcoordination.domain.FrequencyOccupancy;
import com.chris64233.spectrumcoordination.domain.FrequencyResource;
import com.chris64233.spectrumcoordination.domain.License;
import com.chris64233.spectrumcoordination.domain.LicenseChangeRecord;
import com.chris64233.spectrumcoordination.domain.LicenseStatus;
import com.chris64233.spectrumcoordination.domain.RuleVersion;
import com.chris64233.spectrumcoordination.domain.Station;
import com.chris64233.spectrumcoordination.repository.ApplicationConflictRepository;
import com.chris64233.spectrumcoordination.repository.FrequencyApplicationRepository;
import com.chris64233.spectrumcoordination.repository.FrequencyOccupancyRepository;
import com.chris64233.spectrumcoordination.repository.FrequencyResourceRepository;
import com.chris64233.spectrumcoordination.repository.LicenseChangeRecordRepository;
import com.chris64233.spectrumcoordination.repository.LicenseRepository;
import com.chris64233.spectrumcoordination.repository.CoordinationLockRepository;
import com.chris64233.spectrumcoordination.repository.StationRepository;
import com.chris64233.spectrumcoordination.web.dto.ApplicationSubmitRequest;
import com.chris64233.spectrumcoordination.web.dto.ReassignRequest;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 频率使用许可核心协调服务。
 *
 * <p>所有改变占用事实的操作（批准、改频、暂停、终止）都在单事务内先获取全局干扰协调锁，
 * 再读占用、做"时间 × 频段 × 干扰区域"三重判定、原子写入。因此相邻区域同一频段的
 * 并发申请最终结果不可能违反 {@code maxCoChannelUsers} 规则。
 */
@Service
public class SpectrumCoordinationService {

    private final StationRepository stationRepository;
    private final FrequencyResourceRepository resourceRepository;
    private final FrequencyApplicationRepository applicationRepository;
    private final LicenseRepository licenseRepository;
    private final FrequencyOccupancyRepository occupancyRepository;
    private final ApplicationConflictRepository conflictRepository;
    private final LicenseChangeRecordRepository changeRecordRepository;
    private final RuleVersionService ruleVersionService;
    private final InterferenceEvaluator evaluator;
    private final CoordinationLockRepository coordinationLockRepository;

    public SpectrumCoordinationService(StationRepository stationRepository,
                                       FrequencyResourceRepository resourceRepository,
                                       FrequencyApplicationRepository applicationRepository,
                                       LicenseRepository licenseRepository,
                                       FrequencyOccupancyRepository occupancyRepository,
                                       ApplicationConflictRepository conflictRepository,
                                       LicenseChangeRecordRepository changeRecordRepository,
                                       RuleVersionService ruleVersionService,
                                       InterferenceEvaluator evaluator,
                                       CoordinationLockRepository coordinationLockRepository) {
        this.stationRepository = stationRepository;
        this.resourceRepository = resourceRepository;
        this.applicationRepository = applicationRepository;
        this.licenseRepository = licenseRepository;
        this.occupancyRepository = occupancyRepository;
        this.conflictRepository = conflictRepository;
        this.changeRecordRepository = changeRecordRepository;
        this.ruleVersionService = ruleVersionService;
        this.evaluator = evaluator;
        this.coordinationLockRepository = coordinationLockRepository;
    }

    /** 批准结果。 */
    public record ApprovalResult(boolean approved, String applicationNo, String licenseNo,
                                 String ruleVersion, List<ConflictView> conflicts) {
    }

    /** 改频结果。失败时原许可保持有效。 */
    public record ReassignResult(boolean success, String licenseNo, String ruleVersion,
                                 long newBandLowHz, long newBandHighHz,
                                 List<ConflictView> conflicts) {
    }

    public record ConflictView(long licenseId, String licenseNo, long stationId,
                               String ruleVersion, double overlapMeters) {
    }

    // ------------------------------------------------------------------
    // 申请提交（幂等）
    // ------------------------------------------------------------------

    /**
     * 提交申请。相同 applicationNo 重复提交直接返回首次申请，不重复创建（幂等）。
     *
     * <p>刻意不使用方法级事务：先查后插，并发下唯一约束兜底；插入冲突后在独立的新
     * 事务（repository 自带事务）中重新查询，避免在已损坏的持久化上下文里操作。
     */
    public FrequencyApplication submit(ApplicationSubmitRequest req) {
        FrequencyApplication existing =
                applicationRepository.findByApplicationNo(req.applicationNo()).orElse(null);
        if (existing != null) {
            return existing;
        }
        Station station = stationRepository.findById(req.stationId())
                .orElseThrow(() -> new NotFoundException("台站不存在: " + req.stationId()));
        FrequencyResource resource = resourceRepository.findById(req.resourceId())
                .orElseThrow(() -> new NotFoundException("频率资源不存在: " + req.resourceId()));
        validate(req, station, resource);

        RuleVersion rule = ruleVersionService.current();
        double lat = req.usageLatitudeDeg() == null ? station.getLatitudeDeg() : req.usageLatitudeDeg();
        double lon = req.usageLongitudeDeg() == null ? station.getLongitudeDeg() : req.usageLongitudeDeg();
        double radius = req.usageRadiusMeters() == null ? station.getRadiusMeters() : req.usageRadiusMeters();

        FrequencyApplication app = new FrequencyApplication(
                req.applicationNo(), station.getId(), resource.getId(),
                req.bandLowHz(), req.bandHighHz(), lat, lon, radius,
                req.startTime(), req.endTime(), rule.getId());
        try {
            // IDENTITY 主键：insert 在 save 时立即执行，唯一约束冲突在此抛出
            return applicationRepository.saveAndFlush(app);
        } catch (DataIntegrityViolationException e) {
            // 并发提交同一申请号：唯一约束兜底，新事务中返回已存在的申请
            return applicationRepository.findByApplicationNo(req.applicationNo())
                    .orElseThrow(() -> new IllegalStateException(
                            "申请号冲突但无法查询到已存在申请: " + req.applicationNo(), e));
        }
    }

    private void validate(ApplicationSubmitRequest req, Station station, FrequencyResource resource) {
        if (req.bandLowHz() < resource.getBandLowHz() || req.bandHighHz() > resource.getBandHighHz()
                || req.bandLowHz() >= req.bandHighHz()) {
            throw new BusinessRuleException(
                    "申请频段 [" + req.bandLowHz() + "," + req.bandHighHz()
                            + ") 必须完整落在资源频段 [" + resource.getBandLowHz() + ","
                            + resource.getBandHighHz() + ") 内，且下界小于上界");
        }
        if (!req.startTime().isBefore(req.endTime())) {
            throw new BusinessRuleException("开始时间必须早于结束时间");
        }
        double lat = req.usageLatitudeDeg() == null ? station.getLatitudeDeg() : req.usageLatitudeDeg();
        double lon = req.usageLongitudeDeg() == null ? station.getLongitudeDeg() : req.usageLongitudeDeg();
        double radius = req.usageRadiusMeters() == null ? station.getRadiusMeters() : req.usageRadiusMeters();
        if (radius <= 0) {
            throw new BusinessRuleException("使用区域半径必须为正数");
        }
        double centerDistance = GeoCalculator.distanceMeters(
                lat, lon, resource.getRegionLatitudeDeg(), resource.getRegionLongitudeDeg());
        if (centerDistance + radius > resource.getRegionRadiusMeters()) {
            throw new BusinessRuleException("使用区域必须完整落在频率资源所属区域内");
        }
    }

    // ------------------------------------------------------------------
    // 批准（整段频段原子写入；旧规则版本申请按新规则重新校验）
    // ------------------------------------------------------------------

    /**
     * 批准申请。持协调锁后按<b>当前生效规则版本</b>重新校验（无论申请提交时依据哪个版本），
     * 时间 × 频段 × 干扰区域三重重叠数超过 maxCoChannelUsers 即驳回，并落冲突快照。
     * 通过则在同一事务内原子写入整段频率占用与许可——要么整段批准，要么整段驳回。
     */
    @Transactional
    public ApprovalResult approve(String applicationNo) {
        coordinationLockRepository.acquireForCoordination();

        FrequencyApplication app = applicationRepository.findByApplicationNo(applicationNo)
                .orElseThrow(() -> new NotFoundException("申请不存在: " + applicationNo));
        if (app.getStatus() == ApplicationStatus.APPROVED) {
            License license = licenseRepository.findByApplicationId(app.getId()).orElseThrow();
            return new ApprovalResult(true, applicationNo, license.getLicenseNo(),
                    ruleVersionService.get(app.getValidatedRuleVersionId()).getVersion(), List.of());
        }

        RuleVersion rule = ruleVersionService.current();
        Station station = stationRepository.findById(app.getStationId()).orElseThrow();
        FrequencyResource resource = resourceRepository.findById(app.getResourceId()).orElseThrow();

        InterferenceEvaluator.Candidate candidate = new InterferenceEvaluator.Candidate(
                resource.getId(), app.getBandLowHz(), app.getBandHighHz(),
                app.getUsageLatitudeDeg(), app.getUsageLongitudeDeg(), app.getUsageRadiusMeters(),
                station.getPowerWatts(), app.getStartTime(), app.getEndTime());

        List<FrequencyOccupancy> existing = occupancyRepository.findByResourceId(resource.getId());
        InterferenceEvaluator.Evaluation evaluation =
                evaluator.evaluate(candidate, rule, resource, existing, Set.of());

        // 每次校验重建冲突快照，保证查询结果反映最近一次校验
        conflictRepository.deleteByApplicationId(app.getId());
        app.setValidatedRuleVersionId(rule.getId());
        app.markDecided();

        if (!evaluation.allowed()) {
            app.setStatus(ApplicationStatus.REJECTED);
            applicationRepository.save(app);
            List<ConflictView> conflictViews = persistConflicts(app.getId(), evaluation.overlaps(), rule);
            return new ApprovalResult(false, applicationNo, null, rule.getVersion(), conflictViews);
        }

        app.setStatus(ApplicationStatus.APPROVED);
        applicationRepository.save(app);

        LicenseStatus initialStatus = !app.getStartTime().isAfter(Instant.now())
                ? LicenseStatus.ACTIVE : LicenseStatus.NOT_STARTED;
        License license = new License(
                "LIC-" + app.getId(), app.getId(), station.getId(), resource.getId(),
                app.getBandLowHz(), app.getBandHighHz(),
                app.getUsageLatitudeDeg(), app.getUsageLongitudeDeg(), app.getUsageRadiusMeters(),
                app.getStartTime(), app.getEndTime(), initialStatus, rule.getId());
        licenseRepository.save(license);

        occupancyRepository.save(new FrequencyOccupancy(
                license.getId(), station.getId(), resource.getId(),
                app.getBandLowHz(), app.getBandHighHz(),
                app.getUsageLatitudeDeg(), app.getUsageLongitudeDeg(), app.getUsageRadiusMeters(),
                station.getPowerWatts(), app.getStartTime(), app.getEndTime()));

        changeRecordRepository.save(new LicenseChangeRecord(
                license.getId(), ChangeType.ISSUED,
                app.getBandLowHz(), app.getBandHighHz(), app.getBandLowHz(), app.getBandHighHz(),
                rule.getId(), "申请 " + applicationNo + " 批准发证，状态 " + initialStatus));

        return new ApprovalResult(true, applicationNo, license.getLicenseNo(),
                rule.getVersion(), List.of());
    }

    private List<ConflictView> persistConflicts(Long applicationId,
                                                List<InterferenceEvaluator.Overlap> overlaps,
                                                RuleVersion rule) {
        return overlaps.stream().map(o -> {
            conflictRepository.save(new ApplicationConflict(
                    applicationId, o.licenseId(), o.stationId(), rule.getId(), o.overlapMeters()));
            String licenseNo = licenseRepository.findById(o.licenseId())
                    .map(License::getLicenseNo).orElse("?");
            return new ConflictView(o.licenseId(), licenseNo, o.stationId(),
                    rule.getVersion(), o.overlapMeters());
        }).toList();
    }

    // ------------------------------------------------------------------
    // 未开始许可整体改频（新频完整批准后才释放旧频；失败原许可保持有效）
    // ------------------------------------------------------------------

    /**
     * 整体改频。仅 NOT_STARTED 许可可改。同一事务内：获取协调锁 → 校验新频段 →
     * 新频段完整通过后才删除旧占用、写入新占用；任一新占用不通过则不做任何删除，
     * 原许可（旧频段）保持有效，并记录 REASSIGN_FAILED 历史。
     */
    @Transactional
    public ReassignResult reassign(String licenseNo, ReassignRequest req) {
        coordinationLockRepository.acquireForCoordination();

        License license = licenseRepository.findByLicenseNo(licenseNo)
                .orElseThrow(() -> new NotFoundException("许可不存在: " + licenseNo));
        if (license.getStatus() != LicenseStatus.NOT_STARTED) {
            throw new BusinessRuleException(
                    "只有未开始的许可可以整体改频，当前状态: " + license.getStatus()
                            + "；已开始许可只能暂停或提前终止");
        }
        Station station = stationRepository.findById(license.getStationId()).orElseThrow();
        FrequencyResource newResource = resourceRepository.findById(req.resourceId())
                .orElseThrow(() -> new NotFoundException("频率资源不存在: " + req.resourceId()));
        if (req.bandLowHz() < newResource.getBandLowHz()
                || req.bandHighHz() > newResource.getBandHighHz()
                || req.bandLowHz() >= req.bandHighHz()) {
            throw new BusinessRuleException(
                    "新频段必须完整落在资源频段内，且下界小于上界");
        }
        double centerDistance = GeoCalculator.distanceMeters(
                license.getUsageLatitudeDeg(), license.getUsageLongitudeDeg(),
                newResource.getRegionLatitudeDeg(), newResource.getRegionLongitudeDeg());
        if (centerDistance + license.getUsageRadiusMeters() > newResource.getRegionRadiusMeters()) {
            throw new BusinessRuleException("使用区域必须完整落在新频率资源所属区域内");
        }

        RuleVersion rule = ruleVersionService.current();
        InterferenceEvaluator.Candidate candidate = new InterferenceEvaluator.Candidate(
                newResource.getId(), req.bandLowHz(), req.bandHighHz(),
                license.getUsageLatitudeDeg(), license.getUsageLongitudeDeg(),
                license.getUsageRadiusMeters(), station.getPowerWatts(),
                license.getStartTime(), license.getEndTime());

        List<FrequencyOccupancy> existing = occupancyRepository.findByResourceId(newResource.getId());
        InterferenceEvaluator.Evaluation evaluation = evaluator.evaluate(
                candidate, rule, newResource, existing, Set.of(license.getId()));

        if (!evaluation.allowed()) {
            // 校验失败：不动旧占用，原许可继续有效
            String detail = "改频至 [" + req.bandLowHz() + "," + req.bandHighHz()
                    + ") 被拒，冲突许可: " + evaluation.overlaps().stream()
                    .map(o -> licenseRepository.findById(o.licenseId())
                            .map(License::getLicenseNo).orElse("?"))
                    .collect(Collectors.joining(", "));
            changeRecordRepository.save(new LicenseChangeRecord(
                    license.getId(), ChangeType.REASSIGN_FAILED,
                    license.getBandLowHz(), license.getBandHighHz(),
                    req.bandLowHz(), req.bandHighHz(), rule.getId(), detail));
            List<ConflictView> conflictViews = evaluation.overlaps().stream()
                    .map(o -> new ConflictView(o.licenseId(),
                            licenseRepository.findById(o.licenseId())
                                    .map(License::getLicenseNo).orElse("?"),
                            o.stationId(), rule.getVersion(), o.overlapMeters()))
                    .toList();
            return new ReassignResult(false, licenseNo, rule.getVersion(),
                    req.bandLowHz(), req.bandHighHz(), conflictViews);
        }

        // 新频完整批准：同一事务内先写新占用、更新许可，再删旧占用（整体原子，失败全部回滚）
        long oldLow = license.getBandLowHz();
        long oldHigh = license.getBandHighHz();
        occupancyRepository.deleteByLicenseId(license.getId());
        license.setResourceId(newResource.getId());
        license.setBandLowHz(req.bandLowHz());
        license.setBandHighHz(req.bandHighHz());
        licenseRepository.save(license);
        occupancyRepository.save(new FrequencyOccupancy(
                license.getId(), station.getId(), newResource.getId(),
                req.bandLowHz(), req.bandHighHz(),
                license.getUsageLatitudeDeg(), license.getUsageLongitudeDeg(),
                license.getUsageRadiusMeters(), station.getPowerWatts(),
                license.getStartTime(), license.getEndTime()));
        changeRecordRepository.save(new LicenseChangeRecord(
                license.getId(), ChangeType.REASSIGNED,
                oldLow, oldHigh, req.bandLowHz(), req.bandHighHz(), rule.getId(),
                "整体改频成功，旧频率已释放"));
        return new ReassignResult(true, licenseNo, rule.getVersion(),
                req.bandLowHz(), req.bandHighHz(), List.of());
    }

    // ------------------------------------------------------------------
    // 已开始许可：暂停 / 提前终止（保留历史）
    // ------------------------------------------------------------------

    /** 暂停：仅 ACTIVE 许可。占用立即释放，许可行保留为 PAUSED。 */
    @Transactional
    public License pause(String licenseNo) {
        coordinationLockRepository.acquireForCoordination();
        License license = requireLicense(licenseNo);
        if (license.getStatus() != LicenseStatus.ACTIVE) {
            throw new BusinessRuleException("只有已开始（ACTIVE）的许可可以暂停，当前状态: "
                    + license.getStatus());
        }
        occupancyRepository.deleteByLicenseId(license.getId());
        license.markPaused();
        changeRecordRepository.save(new LicenseChangeRecord(
                license.getId(), ChangeType.PAUSED,
                license.getBandLowHz(), license.getBandHighHz(),
                license.getBandLowHz(), license.getBandHighHz(),
                license.getRuleVersionId(), "已暂停，占用即时释放"));
        return license;
    }

    /** 提前终止：未开始/已开始/已暂停许可均可；TERMINATED 行保留，占用释放。 */
    @Transactional
    public License terminate(String licenseNo) {
        coordinationLockRepository.acquireForCoordination();
        License license = requireLicense(licenseNo);
        if (license.getStatus() == LicenseStatus.TERMINATED) {
            throw new BusinessRuleException("许可已终止，不能重复终止");
        }
        occupancyRepository.deleteByLicenseId(license.getId());
        license.markTerminated();
        changeRecordRepository.save(new LicenseChangeRecord(
                license.getId(), ChangeType.TERMINATED,
                license.getBandLowHz(), license.getBandHighHz(),
                license.getBandLowHz(), license.getBandHighHz(),
                license.getRuleVersionId(), "提前终止，占用释放，许可历史保留"));
        return license;
    }

    /** 到达开始时间的未开始许可自动生效（占用在发证时已写入，无需改变占用）。 */
    @Transactional
    public int activateDueLicenses() {
        coordinationLockRepository.acquireForCoordination();
        Instant now = Instant.now();
        int count = 0;
        for (License license : licenseRepository.findByStatus(LicenseStatus.NOT_STARTED)) {
            if (!license.getStartTime().isAfter(now)) {
                license.markStarted();
                changeRecordRepository.save(new LicenseChangeRecord(
                        license.getId(), ChangeType.STARTED,
                        license.getBandLowHz(), license.getBandHighHz(),
                        license.getBandLowHz(), license.getBandHighHz(),
                        license.getRuleVersionId(), "到达开始时间，自动生效"));
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // 查询：许可 / 冲突对象 / 变更记录
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public FrequencyApplication getApplication(String applicationNo) {
        return applicationRepository.findByApplicationNo(applicationNo)
                .orElseThrow(() -> new NotFoundException("申请不存在: " + applicationNo));
    }

    @Transactional(readOnly = true)
    public FrequencyApplication getApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("申请不存在: " + applicationId));
    }

    @Transactional(readOnly = true)
    public License requireLicense(String licenseNo) {
        return licenseRepository.findByLicenseNo(licenseNo)
                .orElseThrow(() -> new NotFoundException("许可不存在: " + licenseNo));
    }

    @Transactional(readOnly = true)
    public List<License> licenses(Long stationId, LicenseStatus status) {
        if (stationId != null) {
            List<License> list = licenseRepository.findByStationId(stationId);
            return status == null ? list : list.stream().filter(l -> l.getStatus() == status).toList();
        }
        return status == null ? licenseRepository.findAll() : licenseRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<ApplicationConflict> conflicts(Long applicationId) {
        return conflictRepository.findByApplicationId(applicationId);
    }

    @Transactional(readOnly = true)
    public List<LicenseChangeRecord> changes(Long licenseId) {
        return changeRecordRepository.findByLicenseIdOrderByOccurredAtAsc(licenseId);
    }
}
