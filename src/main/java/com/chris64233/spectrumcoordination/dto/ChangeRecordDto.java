package com.chris64233.spectrumcoordination.dto;

import java.time.Instant;

import com.chris64233.spectrumcoordination.domain.ChangeType;

public record ChangeRecordDto(Long id, String licenseNo, String applicationNo,
                              ChangeType changeType, String fromStatus, String toStatus,
                              Integer ruleVersionNumber, String detail, Instant changedAt) {
}
