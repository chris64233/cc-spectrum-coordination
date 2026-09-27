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
 * 干扰规则版本。
 *
 * <p>{@code coChannelReuseDistanceKm} 为同频复用保护距离：两个同频占用的台站间距若不超过
 * “双方覆盖半径之和 + 保护距离”，即构成相互干扰。规则版本发生变化后，基于旧版本
 * 提交、尚未批准的申请必须在批准时按新版本重新校验（见申请批准流程）。
 */
@Entity
@Table(name = "interference_rule_version")
public class InterferenceRuleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 版本号，如 1、2，全局唯一 */
    @Column(nullable = false, unique = true)
    private int versionNumber;

    /** 同频复用保护距离，千米 */
    @Column(nullable = false)
    private double coChannelReuseDistanceKm;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RuleVersionStatus status;

    /** 版本生效时间（UTC） */
    @Column(nullable = false)
    private Instant effectiveAt;

    protected InterferenceRuleVersion() {
    }

    public InterferenceRuleVersion(int versionNumber, double coChannelReuseDistanceKm,
                                   String description, RuleVersionStatus status, Instant effectiveAt) {
        this.versionNumber = versionNumber;
        this.coChannelReuseDistanceKm = coChannelReuseDistanceKm;
        this.description = description;
        this.status = status;
        this.effectiveAt = effectiveAt;
    }

    public Long getId() {
        return id;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public double getCoChannelReuseDistanceKm() {
        return coChannelReuseDistanceKm;
    }

    public void setCoChannelReuseDistanceKm(double coChannelReuseDistanceKm) {
        this.coChannelReuseDistanceKm = coChannelReuseDistanceKm;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public RuleVersionStatus getStatus() {
        return status;
    }

    public void setStatus(RuleVersionStatus status) {
        this.status = status;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(Instant effectiveAt) {
        this.effectiveAt = effectiveAt;
    }
}
