package com.chris64233.spectrumcoordination.dto;

import java.time.Instant;

import com.chris64233.spectrumcoordination.domain.ApplicationStatus;

public record ApplicationDto(String applicationNo, String stationCode, Long resourceId,
                             double bandStartMhz, double bandEndMhz,
                             double useLatitude, double useLongitude, double useRadiusKm,
                             Instant startTime, Instant endTime,
                             ApplicationStatus status,
                             int submittedRuleVersion, Integer decisionRuleVersion,
                             String licenseNo, String decisionReason,
                             Instant submittedAt, Instant decidedAt, boolean idempotentReplay) {
}
