package com.chris64233.spectrumcoordination.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record ApplicationSubmitRequest(
        @NotBlank String applicationNo,
        @NotNull Long stationId,
        @NotNull Long resourceId,
        @PositiveOrZero @NotNull Long bandLowHz,
        @PositiveOrZero @NotNull Long bandHighHz,
        /** 使用区域，缺省取台站位置与覆盖半径。 */
        Double usageLatitudeDeg,
        Double usageLongitudeDeg,
        @PositiveOrZero Double usageRadiusMeters,
        @NotNull Instant startTime,
        @NotNull Instant endTime) {
}
