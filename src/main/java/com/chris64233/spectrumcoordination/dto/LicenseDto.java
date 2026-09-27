package com.chris64233.spectrumcoordination.dto;

import java.time.Instant;

public record LicenseDto(String licenseNo, String stationCode, String stationName,
                         Long resourceId, double bandStartMhz, double bandEndMhz,
                         double useLatitude, double useLongitude, double useRadiusKm,
                         Instant originalStartTime, Instant originalEndTime,
                         Instant startTime, Instant endTime,
                         String storedStatus, String effectiveStatus,
                         int ruleVersionNumber, String sourceApplicationNo,
                         Instant createdAt, long version) {
}
