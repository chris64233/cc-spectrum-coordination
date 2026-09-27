package com.chris64233.spectrumcoordination.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 申请冲突快照：记录某申请在校验时与哪些已占用许可冲突（时间、频段、干扰区域三者重叠）。
 *
 * <p>每次校验先清除该申请的旧快照再写入，保证驳回/重校验结果可查询且与最近一次校验一致。
 */
@Entity
@Table(name = "application_conflict", indexes = {
        @Index(name = "idx_conflict_application", columnList = "applicationId")
})
public class ApplicationConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long applicationId;

    @Column(nullable = false)
    private Long conflictLicenseId;

    @Column(nullable = false)
    private Long conflictStationId;

    /** 最近一次校验时使用的规则版本。 */
    @Column(nullable = false)
    private Long ruleVersionId;

    /** 干扰区域重叠量（米）：扩展覆盖圆相交的深度，0 表示仅刚好相切。 */
    @Column(nullable = false)
    private double overlapMeters;

    @Column(nullable = false)
    private Instant detectedAt = Instant.now();

    protected ApplicationConflict() {
    }

    public ApplicationConflict(Long applicationId, Long conflictLicenseId, Long conflictStationId,
                               Long ruleVersionId, double overlapMeters) {
        this.applicationId = applicationId;
        this.conflictLicenseId = conflictLicenseId;
        this.conflictStationId = conflictStationId;
        this.ruleVersionId = ruleVersionId;
        this.overlapMeters = overlapMeters;
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getConflictLicenseId() {
        return conflictLicenseId;
    }

    public Long getConflictStationId() {
        return conflictStationId;
    }

    public Long getRuleVersionId() {
        return ruleVersionId;
    }

    public double getOverlapMeters() {
        return overlapMeters;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }
}
