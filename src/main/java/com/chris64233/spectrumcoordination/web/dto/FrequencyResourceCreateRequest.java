package com.chris64233.spectrumcoordination.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record FrequencyResourceCreateRequest(
        @NotBlank String name,
        @Positive @NotNull Long bandLowHz,
        @Positive @NotNull Long bandHighHz,
        @NotNull Double regionLatitudeDeg,
        @NotNull Double regionLongitudeDeg,
        @Positive @NotNull Double regionRadiusMeters,
        @PositiveOrZero @NotNull Integer maxCoChannelUsers) {
}
