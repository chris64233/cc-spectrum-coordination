package com.chris64233.spectrumcoordination.dto;

import java.time.Instant;

import com.chris64233.spectrumcoordination.domain.RuleVersionStatus;

public record RuleVersionDto(Long id, int versionNumber, double coChannelReuseDistanceKm,
                             String description, RuleVersionStatus status, Instant effectiveAt) {
}
