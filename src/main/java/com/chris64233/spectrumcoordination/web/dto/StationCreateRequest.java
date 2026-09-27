package com.chris64233.spectrumcoordination.web.dto;

import com.chris64233.spectrumcoordination.domain.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StationCreateRequest(
        @NotBlank String name,
        @NotNull Double latitudeDeg,
        @NotNull Double longitudeDeg,
        @Positive @NotNull Double radiusMeters,
        @Positive @NotNull Double powerWatts,
        @NotNull DeviceType deviceType) {
}
