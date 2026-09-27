package com.chris64233.spectrumcoordination.dto;

import jakarta.validation.constraints.Positive;

public record FrequencyResourceRequest(
        @Positive double bandStartMhz,
        @Positive double bandEndMhz,
        double regionLatitude,
        double regionLongitude,
        @Positive double regionRadiusKm,
        @Positive int maxCoChannelUsers) {
}
