package com.chris64233.spectrumcoordination.dto;

import java.time.Instant;

import com.chris64233.spectrumcoordination.domain.ApplicationStatus;
import com.chris64233.spectrumcoordination.domain.ChangeType;

/**
 * 申请裁决结果：批准/拒绝状态、依据的规则版本（可能因版本升级而变化）以及冲突对象列表。
 */
public record DecisionResult(boolean approved,
                             String applicationNo,
                             ApplicationStatus status,
                             ChangeType changeType,
                             int submittedRuleVersion,
                             int decisionRuleVersion,
                             boolean revalidated,
                             String licenseNo,
                             String reason,
                             java.util.List<ConflictDto> conflicts,
                             Instant decidedAt) {
}
