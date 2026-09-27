package com.chris64233.spectrumcoordination.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chris64233.spectrumcoordination.domain.FrequencyOccupancy;
import com.chris64233.spectrumcoordination.domain.FrequencyResource;
import com.chris64233.spectrumcoordination.domain.RuleVersion;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InterferenceEvaluatorTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-01T02:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-01T04:00:00Z");

    /** 余量 1000m：覆盖半径 1000m 的台站扩展半径为 2000m。 */
    private final RuleVersion rule = new RuleVersion("test", 1000.0, 0.0, true);
    private final FrequencyResource resource =
            new FrequencyResource("R", 100_000_000, 200_000_000, 0, 0, 100_000, 1);
    private final InterferenceEvaluator evaluator = new InterferenceEvaluator();

    private InterferenceEvaluator.Candidate candidate(double lonDeg, long low, long high,
                                                      Instant start, Instant end) {
        return new InterferenceEvaluator.Candidate(1L, low, high,
                0.0, lonDeg, 1000.0, 10.0, start, end);
    }

    private FrequencyOccupancy occupancy(long licenseId, double lonDeg, long low, long high,
                                         Instant start, Instant end) {
        return new FrequencyOccupancy(licenseId, licenseId, 1L, low, high,
                0.0, lonDeg, 1000.0, 10.0, start, end);
    }

    @Test
    void noConflictWhenTimeWindowsDisjoint() {
        var result = evaluator.evaluate(
                candidate(0.0, 100_000_000, 150_000_000, T0, T1),
                rule, resource,
                List.of(occupancy(10L, 0.0, 100_000_000, 150_000_000, T1, T2)),
                Set.of());
        assertThat(result.overlaps()).isEmpty();
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void noConflictWhenBandsDisjoint() {
        var result = evaluator.evaluate(
                candidate(0.0, 100_000_000, 120_000_000, T0, T2),
                rule, resource,
                List.of(occupancy(10L, 0.0, 120_000_000, 180_000_000, T0, T2)),
                Set.of());
        assertThat(result.overlaps()).isEmpty();
    }

    @Test
    void noConflictWhenRegionsOutsideInterferenceRange() {
        // 扩展半径各 2000m，圆心相距约 5000m → 不相交
        var result = evaluator.evaluate(
                candidate(0.0, 100_000_000, 150_000_000, T0, T2),
                rule, resource,
                List.of(occupancy(10L, lonForMeters(5000), 100_000_000, 150_000_000, T0, T2)),
                Set.of());
        assertThat(result.overlaps()).isEmpty();
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void conflictWhenTimeBandAndRegionAllOverlap() {
        var result = evaluator.evaluate(
                candidate(0.0, 100_000_000, 150_000_000, T0, T2),
                rule, resource,
                List.of(occupancy(10L, lonForMeters(3000), 100_000_000, 150_000_000, T0, T2)),
                Set.of());
        assertThat(result.overlaps()).hasSize(1);
        assertThat(result.overlaps().get(0).licenseId()).isEqualTo(10L);
        assertThat(result.allowed()).isFalse();
    }

    @Test
    void respectsMaxCoChannelUsers() {
        FrequencyResource tolerant =
                new FrequencyResource("R2", 100_000_000, 200_000_000, 0, 0, 100_000, 2);
        var result = evaluator.evaluate(
                candidate(0.0, 100_000_000, 150_000_000, T0, T2),
                rule, tolerant,
                List.of(occupancy(10L, lonForMeters(3000), 100_000_000, 150_000_000, T0, T2)),
                Set.of());
        assertThat(result.overlaps()).hasSize(1);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void excludesOwnLicenseOccupancyForReassignment() {
        FrequencyOccupancy self = occupancy(7L, 0.0, 100_000_000, 150_000_000, T0, T2);
        var result = evaluator.evaluate(
                candidate(0.0, 100_000_000, 150_000_000, T0, T2),
                rule, resource, List.of(self), Set.of(7L));
        assertThat(result.overlaps()).isEmpty();
        assertThat(result.allowed()).isTrue();
    }

    private static double lonForMeters(double meters) {
        return Math.toDegrees(meters / GeoCalculator.EARTH_RADIUS_METERS);
    }
}
