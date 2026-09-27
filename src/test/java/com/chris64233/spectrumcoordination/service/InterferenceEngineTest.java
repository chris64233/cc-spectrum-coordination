package com.chris64233.spectrumcoordination.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.chris64233.spectrumcoordination.domain.FrequencyOccupancy;
import com.chris64233.spectrumcoordination.domain.FrequencyResource;
import com.chris64233.spectrumcoordination.domain.InterferenceRuleVersion;
import com.chris64233.spectrumcoordination.domain.RuleVersionStatus;
import com.chris64233.spectrumcoordination.domain.Station;

/**
 * 干扰引擎判定的纯单元测试：频段/时间/地理三重重叠与同频使用上限。
 */
class InterferenceEngineTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-01T12:00:00Z");

    private final InterferenceRuleVersion ruleV1 =
            new InterferenceRuleVersion(1, 0.0, "保护距离0", RuleVersionStatus.ACTIVE, T0);
    private final FrequencyResource resource =
            new FrequencyResource(100.0, 200.0, 0, 0, 1000, 1);

    private InterferenceEngine.Candidate candidate(double bandS, double bandE,
                                                   double lat, double lng, double radius) {
        return new InterferenceEngine.Candidate(resource, bandS, bandE, lat, lng, radius, T0, T1);
    }

    private FrequencyOccupancy occupancy(double bandS, double bandE,
                                         double lat, double lng, double radius) {
        Station station = new Station("S", "station", lat, lng, radius, 10, "A");
        var license = new com.chris64233.spectrumcoordination.domain.License(
                "LIC-00000001", station, resource, bandS, bandE, lat, lng, radius,
                T0, T1, 1, "APP-1", T0);
        return new FrequencyOccupancy(license, resource, bandS, bandE, lat, lng, radius, T0, T1);
    }

    @Test
    void bandOverlapUsesHalfOpenIntervals() {
        assertThat(InterferenceEngine.bandOverlaps(100, 110, 110, 120)).isFalse();
        assertThat(InterferenceEngine.bandOverlaps(100, 110, 105, 115)).isTrue();
        assertThat(InterferenceEngine.bandOverlaps(105, 115, 100, 110)).isTrue();
    }

    @Test
    void timeOverlapUsesHalfOpenIntervals() {
        assertThat(InterferenceEngine.timeOverlaps(T0, T1, T1, T1.plusSeconds(3600))).isFalse();
        assertThat(InterferenceEngine.timeOverlaps(T0, T1, T0.plusSeconds(60), T1.plusSeconds(60))).isTrue();
    }

    @Test
    void circlesTouchingAreInterfering() {
        // 两个半径 10km 的圆，圆心相距正好 20km（保护距离 0），判定相交
        var result = InterferenceEngine.check(
                candidate(100, 110, 0.0, 0.0, 10),
                List.of(occupancy(100, 110, 0.17976, 0.0, 10)), ruleV1, null);
        assertThat(result.allowed()).isFalse();
        assertThat(result.interferingCount()).isEqualTo(1);
    }

    @Test
    void separatedCirclesDoNotInterfere() {
        // 圆心相距约 55km，半径和 20km，保护距离 0，不干扰
        var result = InterferenceEngine.check(
                candidate(100, 110, 0.0, 0.0, 10),
                List.of(occupancy(100, 110, 0.5, 0.0, 10)), ruleV1, null);
        assertThat(result.allowed()).isTrue();
        assertThat(result.interferingCount()).isZero();
    }

    @Test
    void nonOverlappingBandIsAllowedEvenAtSameLocation() {
        var result = InterferenceEngine.check(
                candidate(100, 110, 0, 0, 50),
                List.of(occupancy(110, 120, 0, 0, 50)), ruleV1, null);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void reuseDistanceExpandsInterferenceRange() {
        var ruleWithProtection =
                new InterferenceRuleVersion(2, 30.0, "保护距离30km", RuleVersionStatus.ACTIVE, T0);
        // 圆心距约 55km，半径和 20km：v1（保护 0）不干扰，v2（保护 30km）要求间距 > 50km，55km 仍不干扰
        var v1Result = InterferenceEngine.check(
                candidate(100, 110, 0, 0, 10),
                List.of(occupancy(100, 110, 0.5, 0, 10)), ruleV1, null);
        var v2Result = InterferenceEngine.check(
                candidate(100, 110, 0, 0, 10),
                List.of(occupancy(100, 110, 0.5, 0, 10)), ruleWithProtection, null);
        assertThat(v1Result.allowed()).isTrue();
        assertThat(v2Result.allowed()).isTrue();

        // 圆心距约 44km，半径和 20 + 保护 30 = 50km，v2 下构成干扰
        var v2Near = InterferenceEngine.check(
                candidate(100, 110, 0, 0, 10),
                List.of(occupancy(100, 110, 0.4, 0, 10)), ruleWithProtection, null);
        assertThat(v2Near.allowed()).isFalse();
        assertThat(v2Near.ruleVersionNumber()).isEqualTo(2);
    }

    @Test
    void coChannelCapacityAllowsMultipleUsers() {
        var capacityTwo = new FrequencyResource(100, 200, 0, 0, 1000, 2);
        var cand = new InterferenceEngine.Candidate(capacityTwo, 100, 110, 0, 0, 10, T0, T1);
        var occ1 = occupancy(100, 110, 0, 0, 10);
        var result = InterferenceEngine.check(cand, List.of(occ1), ruleV1, null);
        // 已有 1 个干扰对象 + 本次 = 2，未超过上限 2
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void excludeLicenseFiltersSelfOccupancy() {
        var occ = occupancy(100, 110, 0, 0, 10);
        Long selfId = occ.getLicense().getId();
        // 未持久化时 id 为 null，排除不生效
        assertThat(selfId).isNull();
        var included = InterferenceEngine.check(
                candidate(100, 110, 0, 0, 10), List.of(occ), ruleV1, null);
        assertThat(included.allowed()).isFalse();
    }
}
