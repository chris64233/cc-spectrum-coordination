package com.chris64233.spectrumcoordination.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoCalculatorTest {

    @Test
    void distanceBetweenKnownPoints() {
        // 赤道上经度相差 0.01 度 ≈ 1111.95 米
        double d = GeoCalculator.distanceMeters(0.0, 0.0, 0.0, 0.01);
        assertThat(d).isCloseTo(1111.95, within(1.0));
    }

    @Test
    void distanceFromPointToItselfIsZero() {
        assertThat(GeoCalculator.distanceMeters(39.9, 116.4, 39.9, 116.4)).isZero();
    }

    @Test
    void circlesOverlapWhenCentersClose() {
        // 两圆心相距 1500m，半径各 1000m → 重叠 500m
        double overlap = GeoCalculator.circleOverlapMeters(
                0.0, 0.0, 1000, 0.0, GeoCalculatorTestSupport.lonForMetersAtEquator(1500), 1000);
        assertThat(overlap).isCloseTo(500.0, within(1.0));
    }

    @Test
    void circlesDoNotOverlapWhenCentersFar() {
        double overlap = GeoCalculator.circleOverlapMeters(
                0.0, 0.0, 1000, 0.0, GeoCalculatorTestSupport.lonForMetersAtEquator(5000), 1000);
        assertThat(overlap).isNegative();
    }

    /** 测试辅助：赤道上给定东向米数对应的经度差。 */
    static final class GeoCalculatorTestSupport {
        static double lonForMetersAtEquator(double meters) {
            return Math.toDegrees(meters / GeoCalculator.EARTH_RADIUS_METERS);
        }

        private GeoCalculatorTestSupport() {
        }
    }
}
