package com.chris64233.spectrumcoordination.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RuleVersionCreateRequest(
        @NotBlank String version,
        @PositiveOrZero @NotNull Double marginMeters,
        @PositiveOrZero @NotNull Double distanceFactor) {
}
