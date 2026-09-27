package com.chris64233.spectrumcoordination.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record RuleVersionRequest(
        @PositiveOrZero double coChannelReuseDistanceKm,
        String description) {
}
