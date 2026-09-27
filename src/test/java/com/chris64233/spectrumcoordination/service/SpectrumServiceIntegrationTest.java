package com.chris64233.spectrumcoordination.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.chris64233.spectrumcoordination.domain.LicenseStatus;
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
import com.chris64233.spectrumcoordination.repo.FrequencyOccupancyRepository;
import com.chris64233.spectrumcoordination.support.DatabaseCleaner;
import com.chris64233.spectrumcoordination.support.MutableClock;
import com.chris64233.spectrumcoordination.support.TestClockConfig;

/**
 * 频谱协调核心业务集成测试。
 */
@SpringBootTest
@Import(TestClockConfig.class)
class SpectrumServiceIntegrationTest {

    @Autowired
    private SpectrumService service;
    @Autowired
    private FrequencyOccupancyRepository occupancyRepo;
    @Autowired
    private MutableClock clock;
    @Autowired
    private DatabaseCleaner cleaner;

    private static final Instant T0 = TestClockConfig.BASE_TIME;

    private long resourceId;
    private static final String STATION_A = "STA-A";
    private static final String STATION_B = "STA-B";

    @BeforeEach
    void setUp() {
        cleaner.clean();
        service.publishRuleVersion(new RuleVersionRequest(0.0, "v1 无保护距离"));
        service.createStation(new StationRequest(STATION_A, "甲台", 0.0, 0.0, 10, 100, "A类"));
        service.createStation(new StationRequest(STATION_B, "乙台", 0.0, 0.0, 10, 100, "A类"));
        resourceId = service.createResource(
                new FrequencyResourceRequest(100, 200, 0, 0, 1000, 1)).getId();
    }

    private ApplicationRequest request(String no, String station, double s, double e,
                                       double lat, double lng, double radius,
                                       Instant start, Instant end) {
        return new ApplicationRequest(no, station, resourceId, s, e, lat, lng, radius, start, end);
    }

    private ApplicationRequest samePlaceRequest(String no, String station, double s, double e) {
        // 时间窗固定在 T0+1h ~ T0+12h，使用圆心 (0,0)、半径 10km
        return request(no, station, s, e, 0, 0, 10,
                T0.plus(1, ChronoUnit.HOURS), T0.plus(12, ChronoUnit.HOURS));
    }

    // ------------------------------------------------------------------
    // 1. 冲突判定：时间 / 频率 / 干扰区域三重重叠
    // ------------------------------------------------------------------

    @Test
    void approveFirstApplicationAndRejectOverlappingSecond() {
        service.submitApplication(samePlaceRequest("APP-1", STATION_A, 100, 110));
        DecisionResult d1 = service.decide("APP-1");
        assertThat(d1.approved()).isTrue();
        assertThat(d1.licenseNo()).startsWith("LIC-");

        // 同频段、同时间、同位置：maxCoChannelUsers=1，必须拒绝
        service.submitApplication(samePlaceRequest("APP-2", STATION_B, 100, 110));
        DecisionResult d2 = service.decide("APP-2");
        assertThat(d2.approved()).isFalse();
        assertThat(d2.status().name()).isEqualTo("REJECTED");
        assertThat(d2.conflicts()).hasSize(1);
        ConflictDto c = d2.conflicts().get(0);
        assertThat(c.licenseNo()).isEqualTo(d1.licenseNo());
        assertThat(c.stationCode()).isEqualTo(STATION_A);

        // 相邻但频段不重叠（左闭右开，110-120 与 100-110 仅端点相接）：批准
        service.submitApplication(samePlaceRequest("APP-3", STATION_B, 110, 120));
        assertThat(service.decide("APP-3").approved()).isTrue();

        // 地理上足够远（圆心距 ~55km > 半径和 20km）：同频也批准
        service.submitApplication(request("APP-4", STATION_B, 100, 110, 0.5, 0, 10,
                T0.plus(1, ChronoUnit.HOURS), T0.plus(12, ChronoUnit.HOURS)));
        assertThat(service.decide("APP-4").approved()).isTrue();
    }

    @Test
    void overlappingTimeWindowsClashButAdjacentDoNot() {
        service.submitApplication(samePlaceRequest("APP-1", STATION_A, 100, 110));
        assertThat(service.decide("APP-1").approved()).isTrue();

        // 时间窗首尾相接 [12h,13h) vs [1h,12h)，不冲突
        service.submitApplication(request("APP-2", STATION_B, 100, 110, 0, 0, 10,
                T0.plus(12, ChronoUnit.HOURS), T0.plus(13, ChronoUnit.HOURS)));
        assertThat(service.decide("APP-2").approved()).isTrue();

        // 时间窗相交 1 秒：冲突
        service.submitApplication(request("APP-3", STATION_B, 100, 110, 0, 0, 10,
                T0.plus(12, ChronoUnit.HOURS).minusSeconds(1),
                T0.plus(13, ChronoUnit.HOURS)));
        DecisionResult d3 = service.decide("APP-3");
        assertThat(d3.approved()).isFalse();
    }

    // ------------------------------------------------------------------
    // 2. 原子整段批准 / 申请号幂等
    // ------------------------------------------------------------------

    @Test
    void approvalIsAtomicForWholeBandAndPersistsSingleOccupancy() {
        // 申请 105-115 与既有 100-110 部分重叠：不能只批其中不重叠部分，整体拒绝
        service.submitApplication(samePlaceRequest("APP-1", STATION_A, 100, 110));
        service.decide("APP-1");
        service.submitApplication(samePlaceRequest("APP-2", STATION_B, 105, 115));
        DecisionResult d2 = service.decide("APP-2");
        assertThat(d2.approved()).isFalse();

        // APP-2 未生成任何许可与占用
        assertThat(service.listLicenses()).hasSize(1);
        assertThat(occupancyRepo.count()).isEqualTo(1);
    }

    @Test
    void applicationNumberIsIdempotentOnSubmitAndDecide() {
        ApplicationRequest req = samePlaceRequest("DUP-1", STATION_A, 100, 110);
        ApplicationDto first = service.submitApplication(req);
        ApplicationDto second = service.submitApplication(req);
        assertThat(second.idempotentReplay()).isTrue();
        assertThat(first.applicationNo()).isEqualTo(second.applicationNo());
        assertThat(service.listApplications()).hasSize(1);

        DecisionResult d1 = service.decide("DUP-1");
        DecisionResult d1Again = service.decide("DUP-1");
        assertThat(d1Again.status()).isEqualTo(d1.status());
        assertThat(d1Again.licenseNo()).isEqualTo(d1.licenseNo());
        assertThat(service.listLicenses()).hasSize(1);
    }

    // ------------------------------------------------------------------
    // 3. 规则版本升级后待批准申请重新校验
    // ------------------------------------------------------------------

    @Test
    void pendingApplicationIsRevalidatedAgainstNewRuleVersionAndRejected() {
        // v1 保护距离 0：距 44km、半径和 20km 不干扰
        service.submitApplication(request("OLD-1", STATION_A, 100, 110, 0.0, 0, 10,
                T0.plus(1, ChronoUnit.HOURS), T0.plus(12, ChronoUnit.HOURS)));
        assertThat(service.decide("OLD-1").approved()).isTrue();

        // 按旧版 v1 提交待批申请（44km 外）
        service.submitApplication(request("OLD-2", STATION_B, 100, 110, 0.4, 0, 10,
                T0.plus(1, ChronoUnit.HOURS), T0.plus(12, ChronoUnit.HOURS)));

        // 规则升级 v2：保护距离 30km，要求间距 > 50km；44km 处构成干扰
        service.publishRuleVersion(new RuleVersionRequest(30.0, "v2 保护距离30km"));

        DecisionResult d = service.decide("OLD-2");
        assertThat(d.approved()).isFalse();
        assertThat(d.submittedRuleVersion()).isEqualTo(1);
        assertThat(d.decisionRuleVersion()).isEqualTo(2);
        assertThat(d.revalidated()).isTrue();

        // 留有 RULE_REVALIDATED 与 REJECTED 两类记录
        var changes = service.changesOfApplication("OLD-2");
        assertThat(changes).extracting(ChangeRecordDto::changeType)
                .contains(com.chris64233.spectrumcoordination.domain.ChangeType.RULE_REVALIDATED,
                        com.chris64233.spectrumcoordination.domain.ChangeType.REJECTED);
    }

    @Test
    void revalidationThatStillPassesApprovesUnderNewVersion() {
        // 按 v1 提交一个位置很远的待批申请
        service.submitApplication(request("OLD-3", STATION_A, 100, 110, 5.0, 5.0, 10,
                T0.plus(1, ChronoUnit.HOURS), T0.plus(12, ChronoUnit.HOURS)));
        // 随后规则升级为 v2
        service.publishRuleVersion(new RuleVersionRequest(30.0, "v2 保护距离30km"));

        DecisionResult d = service.decide("OLD-3");
        assertThat(d.approved()).isTrue();
        assertThat(d.submittedRuleVersion()).isEqualTo(1);
        assertThat(d.revalidated()).isTrue();
        assertThat(d.decisionRuleVersion()).isEqualTo(2);
        assertThat(service.getLicense(d.licenseNo()).ruleVersionNumber()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // 4. 未开始许可整体改频
    // ------------------------------------------------------------------

    @Test
    void retuneBeforeStartSucceedsAndReleasesOldBand() {
        service.submitApplication(samePlaceRequest("RT-1", STATION_A, 100, 110));
        DecisionResult d = service.decide("RT-1");
        String licenseNo = d.licenseNo();

        // 改到空闲频段 130-140（仍在资源 100-200 内）
        LicenseDto retuned = service.retune(licenseNo, new RetuneRequest(resourceId, 130, 140));
        assertThat(retuned.bandStartMhz()).isEqualTo(130);
        assertThat(retuned.bandEndMhz()).isEqualTo(140);
        assertThat(retuned.effectiveStatus()).isEqualTo("NOT_STARTED");

        // 旧频段已释放：同位置 100-110 的新申请可以批准
        service.submitApplication(samePlaceRequest("RT-2", STATION_B, 100, 110));
        assertThat(service.decide("RT-2").approved()).isTrue();
        // 新频段 130-140 已被占用：冲突
        service.submitApplication(samePlaceRequest("RT-3", STATION_B, 130, 140));
        assertThat(service.decide("RT-3").approved()).isFalse();

        var changes = service.changesOfLicense(licenseNo);
        assertThat(changes).extracting(ChangeRecordDto::changeType)
                .contains(com.chris64233.spectrumcoordination.domain.ChangeType.RETUNED);
    }

    @Test
    void failedRetuneKeepsOriginalLicenseValid() {
        // 许可 L1 占用 100-110；许可 L2 占用 130-140。L1 试图改频到 130-140 应失败且自身不变。
        service.submitApplication(samePlaceRequest("RT-10", STATION_A, 100, 110));
        String l1 = service.decide("RT-10").licenseNo();
        service.submitApplication(samePlaceRequest("RT-11", STATION_B, 130, 140));
        String l2 = service.decide("RT-11").licenseNo();
        assertThat(l2).isNotEqualTo(l1);

        assertThatThrownBy(() -> service.retune(l1, new RetuneRequest(resourceId, 130, 140)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("改频失败");

        // 原许可仍在原频段 100-110 且有效；占用数不变（仍是两条）
        LicenseDto original = service.getLicense(l1);
        assertThat(original.bandStartMhz()).isEqualTo(100);
        assertThat(original.bandEndMhz()).isEqualTo(110);
        assertThat(original.effectiveStatus()).isEqualTo("NOT_STARTED");
        assertThat(service.listLicenses()).hasSize(2);
        assertThat(occupancyRepo.count()).isEqualTo(2);

        // 旧频段仍被 L1 占用：新申请依旧冲突
        service.submitApplication(samePlaceRequest("RT-12", STATION_B, 100, 110));
        assertThat(service.decide("RT-12").approved()).isFalse();
    }

    @Test
    void retuneRejectedAfterLicenseStarted() {
        service.submitApplication(samePlaceRequest("RT-20", STATION_A, 100, 110));
        DecisionResult d = service.decide("RT-20");
        clock.setInstant(T0.plus(2, ChronoUnit.HOURS)); // 进入使用时间窗

        LicenseDto license = service.getLicense(d.licenseNo());
        assertThat(license.effectiveStatus()).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> service.retune(d.licenseNo(), new RetuneRequest(resourceId, 130, 140)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("仅未开始");
    }

    // ------------------------------------------------------------------
    // 5. 已开始许可：暂停 / 提前终止 / 历史保留
    // ------------------------------------------------------------------

    @Test
    void suspendKeepsOccupancyAndTerminateReleasesFutureAndKeepsHistory() {
        service.submitApplication(samePlaceRequest("LC-1", STATION_A, 100, 110));
        DecisionResult d = service.decide("LC-1");
        String licenseNo = d.licenseNo();
        clock.setInstant(T0.plus(2, ChronoUnit.HOURS));

        // 暂停后占用保留：新的同频同位置申请仍被拒绝
        service.suspend(licenseNo);
        assertThat(service.getLicense(licenseNo).effectiveStatus()).isEqualTo("SUSPENDED");
        service.submitApplication(samePlaceRequest("LC-2", STATION_B, 100, 110));
        assertThat(service.decide("LC-2").approved()).isFalse();

        // 恢复
        service.resume(licenseNo);
        assertThat(service.getLicense(licenseNo).effectiveStatus()).isEqualTo("ACTIVE");

        // 提前终止：占用截断到当前时刻
        service.terminate(licenseNo);
        LicenseDto terminated = service.getLicense(licenseNo);
        assertThat(terminated.storedStatus()).isEqualTo("TERMINATED");
        assertThat(terminated.endTime()).isEqualTo(T0.plus(2, ChronoUnit.HOURS));
        assertThat(terminated.originalEndTime()).isEqualTo(T0.plus(12, ChronoUnit.HOURS));

        // 终止时刻之后的同频申请不再冲突
        service.submitApplication(request("LC-3", STATION_B, 100, 110, 0, 0, 10,
                T0.plus(3, ChronoUnit.HOURS), T0.plus(11, ChronoUnit.HOURS)));
        assertThat(service.decide("LC-3").approved()).isTrue();

        // 历史完整保留
        var history = service.changesOfLicense(licenseNo);
        assertThat(history).extracting(ChangeRecordDto::changeType).containsExactly(
                com.chris64233.spectrumcoordination.domain.ChangeType.TERMINATED,
                com.chris64233.spectrumcoordination.domain.ChangeType.RESUMED,
                com.chris64233.spectrumcoordination.domain.ChangeType.SUSPENDED,
                com.chris64233.spectrumcoordination.domain.ChangeType.APPROVED);
    }

    @Test
    void lifecycleTransitionsIllegalForWrongStates() {
        service.submitApplication(samePlaceRequest("LC-9", STATION_A, 100, 110));
        String licenseNo = service.decide("LC-9").licenseNo();

        // 未开始不可暂停/终止
        assertThatThrownBy(() -> service.suspend(licenseNo)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.terminate(licenseNo)).isInstanceOf(ApiException.class);
    }

    // ------------------------------------------------------------------
    // 6. 查询：冲突对象、规则版本、变更记录
    // ------------------------------------------------------------------

    @Test
    void conflictPreviewAndChangeQueriesWork() {
        service.submitApplication(samePlaceRequest("Q-1", STATION_A, 100, 110));
        service.decide("Q-1");

        service.submitApplication(samePlaceRequest("Q-2", STATION_B, 100, 110));
        java.util.List<ConflictDto> preview = service.previewConflicts("Q-2");
        assertThat(preview).hasSize(1);
        assertThat(preview.get(0).stationCode()).isEqualTo(STATION_A);

        // 规则版本列表
        assertThat(service.listRuleVersions()).hasSize(1);

        // 最近变更记录
        assertThat(service.latestChanges(10)).isNotEmpty();
    }

    @Test
    void bandOutsideResourceIsRejectedAtSubmit() {
        ApplicationRequest bad = samePlaceRequest("BAD-1", STATION_A, 190, 210);
        assertThatThrownBy(() -> service.submitApplication(bad))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("超出频率资源范围");
    }
}
