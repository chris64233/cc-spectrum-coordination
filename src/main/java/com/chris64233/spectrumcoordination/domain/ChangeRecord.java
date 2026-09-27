package com.chris64233.spectrumcoordination.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 许可/申请变更记录（审计历史）。任何状态变迁与改频均追加一条，不做更新或删除。
 */
@Entity
@Table(name = "change_record")
public class ChangeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联许可编号（申请拒绝等尚无许可的事件可为空） */
    @Column(length = 64)
    private String licenseNo;

    /** 关联申请号 */
    @Column(length = 64)
    private String applicationNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ChangeType changeType;

    /** 变更前状态（可为空） */
    @Column(length = 16)
    private String fromStatus;

    /** 变更后状态 */
    @Column(length = 16)
    private String toStatus;

    /** 变更时依据的规则版本号 */
    private Integer ruleVersionNumber;

    /** 变更详情（频段、时间、原因等的可读摘要） */
    @Column(length = 2000)
    private String detail;

    @Column(nullable = false)
    private Instant changedAt;

    protected ChangeRecord() {
    }

    public ChangeRecord(String licenseNo, String applicationNo, ChangeType changeType,
                        String fromStatus, String toStatus, Integer ruleVersionNumber,
                        String detail, Instant changedAt) {
        this.licenseNo = licenseNo;
        this.applicationNo = applicationNo;
        this.changeType = changeType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.ruleVersionNumber = ruleVersionNumber;
        this.detail = detail;
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public String getLicenseNo() {
        return licenseNo;
    }

    public String getApplicationNo() {
        return applicationNo;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public Integer getRuleVersionNumber() {
        return ruleVersionNumber;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
