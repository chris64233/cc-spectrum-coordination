package com.chris64233.spectrumcoordination.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.spectrumcoordination.domain.ApplicationStatus;
import com.chris64233.spectrumcoordination.domain.ChangeType;
import com.chris64233.spectrumcoordination.domain.DeviceType;
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
import com.chris64233.spectrumcoordination.repository.RuleVersionRepository;
import com.chris64233.spectrumcoordination.repository.StationRepository;
import com.chris64233.spectrumcoordination.web.dto.ApplicationSubmitRequest;
import com.chris64233.spectrumcoordination.web.dto.ReassignRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SpectrumCoordinationServiceTest {

    private static final Instant FUTURE_START = Instant.parse("2026-12-01T00:00:00Z");
    private static final Instant FUTURE_END = Instant.parse("2026-12-01T04:00:00Z");

    @Autowired
    private SpectrumCoordinationService service;
    @Autowired
    private RuleVersionService ruleVersionService;
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private FrequencyResourceRepository resourceRepository;
    @Autowired
    private FrequencyApplicationRepository applicationRepository;
    @Autowired
    private LicenseRepository licenseRepository;
    @Autowired
    private FrequencyOccupancyRepository occupancyRepository;
    @Autowired
    private ApplicationConflictRepository conflictRepository;
    @Autowired
    private LicenseChangeRecordRepository changeRecordRepository;
    @Autowired
    private RuleVersionRepository ruleVersionRepository;
    @Autowired
    private InterferenceEvaluator evaluator;

    @BeforeEach
    void cleanUp() {
        conflictRepository.deleteAllInBatch();
        occupancyRepository.deleteAllInBatch();
        changeRecordRepository.deleteAllInBatch();
        licenseRepository.deleteAllInBatch();
        applicationRepository.deleteAllInBatch();
        resourceRepository.deleteAllInBatch();
        stationRepository.deleteAllInBatch();
        // 恢复 v1 为生效规则版本（v1 为启动初始化版本：余量 1000m + 10m/瓦）
        ruleVersionRepository.findAll().stream()
                .filter(r -> r.getVersion().equals("v1"))
                .findFirst()
                .ifPresent(r -> {
                    if (!r.isActive()) {
                        ruleVersionService.activate(r.getId());
                    }
                });
    }

    private Station station(String name, double lonDeg, double radius, double power) {
        return stationRepository.save(new Station(name, 0.0, lonDeg, radius, power, DeviceType.FIXED));
    }

    private FrequencyResource resource(String name, long low, long high, int maxUsers) {
        return resourceRepository.save(new FrequencyResource(
                name, low, high, 0.0, 0.0, 50_000, maxUsers));
    }

    private FrequencyApplication submit(String no, Station st, FrequencyResource res,
                                        long low, long high, Instant start, Instant end) {
        return service.submit(new ApplicationSubmitRequest(
                no, st.getId(), res.getId(), low, high,
                st.getLatitudeDeg(), st.getLongitudeDeg(), st.getRadiusMeters(), start, end));
    }

    private static double lonForMeters(double meters) {
        return Math.toDegrees(meters / GeoCalculator.EARTH_RADIUS_METERS);
    }

    // ----------------------------------------------------------------
    // 1. 批准成功：原子写入整段占用 + 许可
    // ----------------------------------------------------------------

    @Test
    void approveWritesWholeBandAtomicallyAndCreatesLicense() {
        Station a = station("A", 0.0, 1000, 10);
        FrequencyResource r = resource("R1", 100_000_000, 200_000_000, 1);
        FrequencyApplication app = submit("APP-1", a, r, 100_000_000, 180_000_000,
                FUTURE_START, FUTURE_END);

        SpectrumCoordinationService.ApprovalResult result = service.approve("APP-1");

        assertThat(result.approved()).isTrue();
        assertThat(result.licenseNo()).isEqualTo("LIC-" + app.getId());
        License license = licenseRepository.findByLicenseNo(result.licenseNo()).orElseThrow();
        assertThat(license.getStatus()).isEqualTo(LicenseStatus.NOT_STARTED);
        assertThat(license.getBandLowHz()).isEqualTo(100_000_000);
        assertThat(license.getBandHighHz()).isEqualTo(180_000_000);

        List<FrequencyOccupancy> occupancies = occupancyRepository.findAll();
        assertThat(occupancies).hasSize(1);
        FrequencyOccupancy occ = occupancies.get(0);
        // 整段频段一次性占用，没有被切分
        assertThat(occ.getBandLowHz()).isEqualTo(100_000_000);
        assertThat(occ.getBandHighHz()).isEqualTo(180_000_000);

        List<LicenseChangeRecord> changes = changeRecordRepository
                .findByLicenseIdOrderByOccurredAtAsc(license.getId());
        assertThat(changes).extracting(LicenseChangeRecord::getChangeType)
                .containsExactly(ChangeType.ISSUED);
    }

    // ----------------------------------------------------------------
    // 2. 冲突驳回：三重重叠 + 冲突快照可查询
    // ----------------------------------------------------------------

    @Test
    void conflictingApplicationRejectedWithSnapshot() {
        Station a = station("A", 0.0, 1000, 10);
        Station b = station("B", lonForMeters(3000), 1000, 10);
        FrequencyResource r = resource("R1", 100_000_000, 200_000_000, 1);
        submit("APP-A", a, r, 100_000_000, 150_000_000, FUTURE_START, FUTURE_END);
        service.approve("APP-A");
        FrequencyApplication appB = submit("APP-B", b, r, 100_000_000, 150_000_000,
                FUTURE_START, FUTURE_END);

        SpectrumCoordinationService.ApprovalResult result = service.approve("APP-B");

        assertThat(result.approved()).isFalse();
        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().get(0).licenseNo()).startsWith("LIC-");
        assertThat(applicationRepository.findById(appB.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.REJECTED);
        assertThat(licenseRepository.findByApplicationId(appB.getId())).isEmpty();
        assertThat(occupancyRepository.findAll()).hasSize(1);

        var conflicts = service.conflicts(appB.getId());
        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).getOverlapMeters()).isPositive();
    }

    // ----------------------------------------------------------------
    // 3. 申请号幂等
    // ----------------------------------------------------------------

    @Test
    void duplicateApplicationNoIsIdempotent() {
        Station a = station("A", 0.0, 1000, 10);
        FrequencyResource r = resource("R1", 100_000_000, 200_000_000, 1);
        FrequencyApplication first = submit("DUP-1", a, r, 100_000_000, 150_000_000,
                FUTURE_START, FUTURE_END);
        FrequencyApplication second = submit("DUP-1", a, r, 100_000_000, 150_000_000,
                FUTURE_START, FUTURE_END);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(applicationRepository.count()).isEqualTo(1);
    }

    // ----------------------------------------------------------------
    // 4. 全有或全无：只与已占用频段部分重叠也整段驳回
    // ----------------------------------------------------------------

    @Test
    void partialBandOverlapRejectsWholeApplication() {
        Station a = station("A", 0.0, 1000, 10);
        Station b = station("B", lonForMeters(3000), 1000, 10);
        FrequencyResource r = resource("R1", 100_000_000, 200_000_000, 1);
        submit("APP-A", a, r, 100_000_000, 150_000_000, FUTURE_START, FUTURE_END);
        service.approve("APP-A");
        // B 的频段 [120M,180M) 只与 A 的 [100M,150M) 部分重叠
        FrequencyApplication appB = submit("APP-B", b, r, 120_000_000, 180_000_000,
                FUTURE_START, FUTURE_END);

        SpectrumCoordinationService.ApprovalResult result = service.approve("APP-B");

        assertThat(result.approved()).isFalse();
        assertThat(licenseRepository.findByApplicationId(appB.getId())).isEmpty();
        // 不允许只批准无冲突的 [150M,180M) 一段
        assertThat(occupancyRepository.findAll()).hasSize(1);
        assertThat(occupancyRepository.findAll().get(0).getBandLowHz()).isEqualTo(100_000_000);
    }

    // ----------------------------------------------------------------
    // 5. 未开始许可整体改频：成功后释放旧频率
    // ----------------------------------------------------------------

    @Test
    void notStartedLicenseReassignsAtomicallyReleasingOldBandAfterNewApproved() {
        Station a = station("A", 0.0, 1000, 10);
        FrequencyResource r1 = resource("R1", 100_000_000, 200_000_000, 1);
        FrequencyResource r2 = resource("R2", 300_000_000, 400_000_000, 1);
        submit("APP-A", a, r1, 100_000_000, 150_000_000, FUTURE_START, FUTURE_END);
        var approval = service.approve("APP-A");

        var result = service.reassign(approval.licenseNo(),
                new ReassignRequest(r2.getId(), 300_000_000L, 350_000_000L));

        assertThat(result.success()).isTrue();
        License license = licenseRepository.findByLicenseNo(approval.licenseNo()).orElseThrow();
        assertThat(license.getResourceId()).isEqualTo(r2.getId());
        assertThat(license.getBandLowHz()).isEqualTo(300_000_000);
        assertThat(license.getBandHighHz()).isEqualTo(350_000_000);
        assertThat(license.getStatus()).isEqualTo(LicenseStatus.NOT_STARTED);

        FrequencyOccupancy occ = occupancyRepository.findAll().get(0);
        assertThat(occ.getResourceId()).isEqualTo(r2.getId());
        assertThat(occ.getBandLowHz()).isEqualTo(300_000_000);
        assertThat(occ.getBandHighHz()).isEqualTo(350_000_000);

        List<LicenseChangeRecord> changes = service.changes(license.getId());
        assertThat(changes).extracting(LicenseChangeRecord::getChangeType)
                .containsExactly(ChangeType.ISSUED, ChangeType.REASSIGNED);
        LicenseChangeRecord reassignLog = changes.get(1);
        assertThat(reassignLog.getFromBandLowHz()).isEqualTo(100_000_000);
        assertThat(reassignLog.getToBandLowHz()).isEqualTo(300_000_000);
    }

    // ----------------------------------------------------------------
    // 6. 改频失败：新频未完整批准，原许可保持有效
    // ----------------------------------------------------------------

    @Test
    void failedReassignKeepsOriginalLicenseAndOccupancy() {
        Station a = station("A", 0.0, 1000, 10);
        Station b = station("B", lonForMeters(3000), 1000, 10);
        FrequencyResource r1 = resource("R1", 100_000_000, 200_000_000, 1);
        FrequencyResource r2 = resource("R2", 300_000_000, 400_000_000, 1);
        submit("APP-A", a, r1, 100_000_000, 150_000_000, FUTURE_START, FUTURE_END);
        service.approve("APP-A");
        submit("APP-B", b, r2, 300_000_000, 350_000_000, FUTURE_START, FUTURE_END);
        service.approve("APP-B");

        License licenseA = licenseRepository.findByStationId(a.getId()).get(0);
        var result = service.reassign(licenseA.getLicenseNo(),
                new ReassignRequest(r2.getId(), 300_000_000L, 350_000_000L));

        assertThat(result.success()).isFalse();
        assertThat(result.conflicts()).hasSize(1);
        License reloaded = licenseRepository.findByLicenseNo(licenseA.getLicenseNo()).orElseThrow();
        assertThat(reloaded.getResourceId()).isEqualTo(r1.getId());
        assertThat(reloaded.getBandLowHz()).isEqualTo(100_000_000);
        assertThat(reloaded.getStatus()).isEqualTo(LicenseStatus.NOT_STARTED);

        // 旧占用完整保留，新频段没有写入任何占用
        List<FrequencyOccupancy> occs = occupancyRepository.findAll();
        assertThat(occs).hasSize(2);
        FrequencyOccupancy old = occs.stream()
                .filter(o -> o.getLicenseId().equals(licenseA.getId())).findFirst().orElseThrow();
        assertThat(old.getResourceId()).isEqualTo(r1.getId());
        assertThat(old.getBandLowHz()).isEqualTo(100_000_000);

        List<LicenseChangeRecord> changes = service.changes(licenseA.getId());
        assertThat(changes).extracting(LicenseChangeRecord::getChangeType)
                .containsExactly(ChangeType.ISSUED, ChangeType.REASSIGN_FAILED);
    }

    // ----------------------------------------------------------------
    // 7. 已开始许可不能改频
    // ----------------------------------------------------------------

    @Test
    void activeLicenseCannotReassign() {
        Station a = station("A", 0.0, 1000, 10);
        FrequencyResource r1 = resource("R1", 100_000_000, 200_000_000, 1);
        FrequencyResource r2 = resource("R2", 300_000_000, 400_000_000, 1);
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(2, ChronoUnit.HOURS);
        submit("APP-A", a, r1, 100_000_000, 150_000_000, start, end);
        var approval = service.approve("APP-A");
        assertThat(licenseRepository.findByLicenseNo(approval.licenseNo()).orElseThrow().getStatus())
                .isEqualTo(LicenseStatus.ACTIVE);

        assertThatThrownBy(() -> service.reassign(approval.licenseNo(),
                new ReassignRequest(r2.getId(), 300_000_000L, 350_000_000L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("只有未开始");
    }

    // ----------------------------------------------------------------
    // 8. 已开始许可：暂停 / 提前终止，历史保留
    // ----------------------------------------------------------------

    @Test
    void activeLicenseCanPauseAndTerminateWithHistory() {
        Station a = station("A", 0.0, 1000, 10);
        FrequencyResource r = resource("R1", 100_000_000, 200_000_000, 1);
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(2, ChronoUnit.HOURS);
        submit("APP-A", a, r, 100_000_000, 150_000_000, start, end);
        var approval = service.approve("APP-A");

        License paused = service.pause(approval.licenseNo());
        assertThat(paused.getStatus()).isEqualTo(LicenseStatus.PAUSED);
        assertThat(paused.getPausedAt()).isNotNull();
        // 暂停立即释放占用
        assertThat(occupancyRepository.findByLicenseId(paused.getId())).isEmpty();

        assertThatThrownBy(() -> service.pause(approval.licenseNo()))
                .isInstanceOf(BusinessRuleException.class);

        License terminated = service.terminate(approval.licenseNo());
        assertThat(terminated.getStatus()).isEqualTo(LicenseStatus.TERMINATED);
        assertThat(terminated.getTerminatedAt()).isNotNull();
        assertThat(occupancyRepository.findByLicenseId(terminated.getId())).isEmpty();

        // 许可行与完整变更历史保留
        assertThat(licenseRepository.findByLicenseNo(approval.licenseNo())).isPresent();
        List<LicenseChangeRecord> changes = service.changes(terminated.getId());
        assertThat(changes).extracting(LicenseChangeRecord::getChangeType)
                .containsExactly(ChangeType.ISSUED, ChangeType.PAUSED, ChangeType.TERMINATED);
    }

    // ----------------------------------------------------------------
    // 9. 规则版本变更：旧版本待批准申请按新版本重新校验
    // ----------------------------------------------------------------

    @Test
    void pendingApplicationRevalidatedAgainstNewRuleVersion() {
        // v1 余量：每台站扩展 1000(覆盖) + 1000(余量) + 10*10 = 2100m
        Station a = station("A", 0.0, 1000, 10);
        Station b = station("B", lonForMeters(4500), 1000, 10);
        FrequencyResource r = resource("R1", 100_000_000, 200_000_000, 1);

        submit("APP-A", a, r, 100_000_000, 150_000_000, FUTURE_START, FUTURE_END);
        service.approve("APP-A");
        FrequencyApplication appB = submit("APP-B", b, r, 100_000_000, 150_000_000,
                FUTURE_START, FUTURE_END);
        // v1 下两扩展圆半径和 4200m < 4500m，本不冲突；此时不批准 B
        RuleVersion v2 = ruleVersionService.create("v2-strict", 3000.0, 10.0);
        ruleVersionService.activate(v2.getId());

        // 规则变化后批准：B 基于 v1 提交，必须按 v2 重新校验 → 扩展半径和 8200m > 4500m，驳回
        var result = service.approve("APP-B");

        assertThat(result.approved()).isFalse();
        assertThat(result.ruleVersion()).isEqualTo("v2-strict");
        FrequencyApplication reloaded = applicationRepository.findById(appB.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(reloaded.getValidatedRuleVersionId()).isEqualTo(v2.getId());
        assertThat(service.conflicts(appB.getId())).hasSize(1);
        assertThat(service.conflicts(appB.getId()).get(0).getRuleVersionId()).isEqualTo(v2.getId());
    }

    // ----------------------------------------------------------------
    // 10. 并发：相邻区域同一频段，最终占用不得违反干扰规则
    // ----------------------------------------------------------------

    @Test
    void concurrentAdjacentApplicationsNeverViolateInterferenceRule() throws Exception {
        for (int round = 0; round < 5; round++) {
            int n = 6;
            FrequencyResource r = resource("R1-" + round, 100_000_000, 200_000_000, 1);
            List<Station> stations = new ArrayList<>();
            // 相邻台站相距 3000m（v1 下扩展半径和 4200m，相邻对必冲突）
            for (int i = 0; i < n; i++) {
                stations.add(station("S" + round + "-" + i, lonForMeters(3000.0 * i), 1000, 10));
            }
            List<FrequencyApplication> apps = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                apps.add(submit("CONC-" + round + "-" + i, stations.get(i), r,
                        100_000_000, 150_000_000, FUTURE_START, FUTURE_END));
            }

            ExecutorService pool = Executors.newFixedThreadPool(n);
            try {
                List<Callable<SpectrumCoordinationService.ApprovalResult>> tasks = new ArrayList<>();
                for (FrequencyApplication app : apps) {
                    String no = app.getApplicationNo();
                    tasks.add(() -> service.approve(no));
                }
                List<Future<SpectrumCoordinationService.ApprovalResult>> futures = pool.invokeAll(tasks);
                for (Future<SpectrumCoordinationService.ApprovalResult> f : futures) {
                    f.get(); // 任何异常都会使测试失败
                }
            } finally {
                pool.shutdown();
            }

            // 只有第 0、2、4 号台站的扩展圆互不相交（相距 6000m > 4200m），
            // 因此排他规则下最多 3 个批准，至少 1 个
            long approvedCount = apps.stream().filter(a -> reloadedApproved(a.getId())).count();
            assertThat(approvedCount).isBetween(1L, 3L);
            List<FrequencyOccupancy> occupancies = occupancyRepository.findByResourceId(r.getId());
            assertThat(occupancies).hasSize((int) approvedCount);

            // 核心不变式：任意两个同时间同频段占用，扩展覆盖圆不得相交（maxUsers=1）
            RuleVersion rule = ruleVersionService.current();
            for (int i = 0; i < occupancies.size(); i++) {
                for (int j = i + 1; j < occupancies.size(); j++) {
                    FrequencyOccupancy x = occupancies.get(i);
                    FrequencyOccupancy y = occupancies.get(j);
                    double rx = x.getUsageRadiusMeters()
                            + rule.interferenceAllowanceMeters(x.getPowerWatts());
                    double ry = y.getUsageRadiusMeters()
                            + rule.interferenceAllowanceMeters(y.getPowerWatts());
                    double overlap = GeoCalculator.circleOverlapMeters(
                            x.getUsageLatitudeDeg(), x.getUsageLongitudeDeg(), rx,
                            y.getUsageLatitudeDeg(), y.getUsageLongitudeDeg(), ry);
                    assertThat(overlap)
                            .as("第 %d 轮并发批准结果违反干扰规则: licenses %d 与 %d",
                                    round, x.getLicenseId(), y.getLicenseId())
                            .isLessThanOrEqualTo(0);
                }
            }
        }
    }

    @Test
    void concurrentSubmitSameApplicationNoCreatesExactlyOne() throws Exception {
        Station a = station("A-CC", 0.0, 1000, 10);
        FrequencyResource r = resource("R1-CC", 100_000_000, 200_000_000, 1);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<FrequencyApplication>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(() -> submit("CONC-DUP", a, r, 100_000_000, 150_000_000,
                        FUTURE_START, FUTURE_END));
            }
            List<Future<FrequencyApplication>> futures = pool.invokeAll(tasks);
            Set<Long> ids = new java.util.HashSet<>();
            for (Future<FrequencyApplication> f : futures) {
                ids.add(f.get().getId());
            }
            assertThat(ids).hasSize(1);
        } finally {
            pool.shutdown();
        }
        assertThat(applicationRepository.count()).isEqualTo(1);
    }

    private boolean reloadedApproved(Long id) {
        return applicationRepository.findById(id)
                .map(a -> a.getStatus() == ApplicationStatus.APPROVED).orElse(false);
    }

    // ----------------------------------------------------------------
    // 11. 查询：许可 / 冲突 / 规则版本 / 变更记录
    // ----------------------------------------------------------------

    @Test
    void queryEndpointsBackedByServiceReturnExpectedData() {
        Station a = station("A", 0.0, 1000, 10);
        Station b = station("B", lonForMeters(3000), 1000, 10);
        FrequencyResource r = resource("R1", 100_000_000, 200_000_000, 1);
        submit("APP-A", a, r, 100_000_000, 150_000_000, FUTURE_START, FUTURE_END);
        service.approve("APP-A");
        FrequencyApplication appB = submit("APP-B", b, r, 100_000_000, 150_000_000,
                FUTURE_START, FUTURE_END);
        service.approve("APP-B");

        // 许可查询：按台站、按状态
        assertThat(service.licenses(a.getId(), null)).hasSize(1);
        assertThat(service.licenses(null, LicenseStatus.NOT_STARTED)).hasSize(1);
        assertThat(service.licenses(null, LicenseStatus.ACTIVE)).isEmpty();

        // 冲突对象查询
        assertThat(service.conflicts(appB.getId())).hasSize(1);

        // 规则版本查询
        List<RuleVersion> versions = ruleVersionService.all();
        assertThat(versions).extracting(RuleVersion::getVersion).contains("v1");
        assertThat(ruleVersionService.current().getVersion()).isEqualTo("v1");

        // 变更记录查询
        License license = service.licenses(a.getId(), null).get(0);
        assertThat(service.changes(license.getId())).extracting(LicenseChangeRecord::getChangeType)
                .containsExactly(ChangeType.ISSUED);
    }

    @Test
    void applicationBandOutsideResourceBandRejected() {
        Station a = station("A", 0.0, 1000, 10);
        FrequencyResource r = resource("R1", 100_000_000, 200_000_000, 1);
        assertThatThrownBy(() -> submit("BAD-1", a, r, 150_000_000, 250_000_000,
                FUTURE_START, FUTURE_END))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("频段");
    }
}
