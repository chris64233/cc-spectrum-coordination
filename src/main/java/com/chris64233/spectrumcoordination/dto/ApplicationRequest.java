package com.chris64233.spectrumcoordination.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 频率使用许可申请请求。
 *
 * @param useRadiusKm 使用区域半径，&le;0 时回退为台站覆盖半径
 */
public record ApplicationRequest(
        @NotBlank String applicationNo,
        @NotBlank String stationCode,
        @NotNull Long resourceId,
        @Positive double bandStartMhz,
        @Positive double bandEndMhz,
        double useLatitude,
        double useLongitude,
        double useRadiusKm,
        @NotNull Instant startTime,
        @NotNull Instant endTime) {
}
