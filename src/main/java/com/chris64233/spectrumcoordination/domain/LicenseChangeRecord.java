package com.chris64233.spectrumcoordination.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 许可变更记录（历史，仅追加）：发证、生效、改频成功/失败、暂停、终止。
 */
@Entity
@Table(name = "license_change_record", indexes = {
        @Index(name = "idx_lcr_license", columnList = "licenseId")
})
public class LicenseChangeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long licenseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ChangeType changeType;

    /** 变更前频段（Hz），无频段变化时与变更后相同。 */
    @Column(nullable = false)
    private long fromBandLowHz;

    @Column(nullable = false)
    private long fromBandHighHz;

    /** 变更后频段（Hz）。 */
    @Column(nullable = false)
    private long toBandLowHz;

    @Column(nullable = false)
    private long toBandHighHz;

    @Column(nullable = false)
    private Long ruleVersionId;

    @Column(length = 512)
    private String detail;

    @Column(nullable = false)
    private Instant occurredAt = Instant.now();

    protected LicenseChangeRecord() {
    }

    public LicenseChangeRecord(Long licenseId, ChangeType changeType,
                               long fromBandLowHz, long fromBandHighHz,
                               long toBandLowHz, long toBandHighHz,
                               Long ruleVersionId, String detail) {
        this.licenseId = licenseId;
        this.changeType = changeType;
        this.fromBandLowHz = fromBandLowHz;
        this.fromBandHighHz = fromBandHighHz;
        this.toBandLowHz = toBandLowHz;
        this.toBandHighHz = toBandHighHz;
        this.ruleVersionId = ruleVersionId;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public Long getLicenseId() {
        return licenseId;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public long getFromBandLowHz() {
        return fromBandLowHz;
    }

    public long getFromBandHighHz() {
        return fromBandHighHz;
    }

    public long getToBandLowHz() {
        return toBandLowHz;
    }

    public long getToBandHighHz() {
        return toBandHighHz;
    }

    public Long getRuleVersionId() {
        return ruleVersionId;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
