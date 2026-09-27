package com.chris64233.spectrumcoordination.dto;

public record StationDto(Long id, String stationCode, String name,
                         double latitude, double longitude,
                         double coverageRadiusKm, double transmitPowerW, String deviceType) {
}
