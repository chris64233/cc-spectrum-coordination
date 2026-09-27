package com.chris64233.spectrumcoordination.dto;

public record ConflictDto(String licenseNo, String stationCode,
                          double bandStartMhz, double bandEndMhz,
                          double latitude, double longitude, double radiusKm,
                          double distanceKm, double requiredSeparationKm, String reason) {
}
