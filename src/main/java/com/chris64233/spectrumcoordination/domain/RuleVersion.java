package com.chris64233.spectrumcoordination.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 干扰规则版本。
 *
 * <p>任意时刻最多只有一个 {@code active=true} 的版本。申请提交时记录当时的生效版本；
 * 批准前若发现申请基于旧版本，则按当前生效版本重新校验干扰。
 *
 * @param marginMeters 固定同频干扰余量（米）：在各自覆盖圆基础上叠加
 * @param distanceFactor 距离系数：与发射功率相乘得到功率相关余量，总余量 =
 *                       marginMeters + distanceFactor * 功率(瓦)
 */
@Entity
@Table(name = "rule_version")
public class RuleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String version;

    @Column(nullable = false)
    private double marginMeters;

    @Column(nullable = false)
    private double distanceFactor;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** 成为生效版本的时间（规则版本变更点）。 */
    private Instant activatedAt;

    protected RuleVersion() {
    }

    public RuleVersion(String version, double marginMeters, double distanceFactor, boolean active) {
        this.version = version;
        this.marginMeters = marginMeters;
        this.distanceFactor = distanceFactor;
        this.active = active;
        if (active) {
            this.activatedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public double getMarginMeters() {
        return marginMeters;
    }

    public double getDistanceFactor() {
        return distanceFactor;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void activate() {
        this.active = true;
        this.activatedAt = Instant.now();
    }

    /** 单个台站在本规则版本下的干扰扩展余量（米）。 */
    public double interferenceAllowanceMeters(double powerWatts) {
        return marginMeters + distanceFactor * powerWatts;
    }
}
