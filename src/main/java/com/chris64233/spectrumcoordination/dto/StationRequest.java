package com.chris64233.spectrumcoordination.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record StationRequest(
        @NotBlank String stationCode,
        @NotBlank String name,
        double latitude,
        double longitude,
        @Positive double coverageRadiusKm,
        @Positive double transmitPowerW,
        @NotBlank String deviceType) {
}
