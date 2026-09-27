package com.chris64233.spectrumcoordination.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 频率使用许可。批准申请时生成，整段频段原子占用。
 *
 * <p>未开始（{@link LicenseStatus#NOT_STARTED}）的许可可以整体改频；已开始的许可
 * 只能暂停或提前终止。许可行永久保留作为历史。
 */
@Entity
@Table(name = "license")
public class License {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String licenseNo;

    @Column(nullable = false)
    private Long applicationId;

    @Column(nullable = false)
    private Long stationId;

    @Column(nullable = false)
    private Long resourceId;

    @Column(nullable = false)
    private long bandLowHz;

    @Column(nullable = false)
    private long bandHighHz;

    @Column(nullable = false)
    private double usageLatitudeDeg;

    @Column(nullable = false)
    private double usageLongitudeDeg;

    @Column(nullable = false)
    private double usageRadiusMeters;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LicenseStatus status;

    /** 批准时依据的规则版本。 */
    @Column(nullable = false)
    private Long ruleVersionId;

    @Column(nullable = false)
    private Instant issuedAt = Instant.now();

    private Instant startedAt;

    private Instant pausedAt;

    private Instant terminatedAt;

    protected License() {
    }

    public License(String licenseNo, Long applicationId, Long stationId, Long resourceId,
                   long bandLowHz, long bandHighHz,
                   double usageLatitudeDeg, double usageLongitudeDeg, double usageRadiusMeters,
                   Instant startTime, Instant endTime, LicenseStatus status, Long ruleVersionId) {
        this.licenseNo = licenseNo;
        this.applicationId = applicationId;
        this.stationId = stationId;
        this.resourceId = resourceId;
        this.bandLowHz = bandLowHz;
        this.bandHighHz = bandHighHz;
        this.usageLatitudeDeg = usageLatitudeDeg;
        this.usageLongitudeDeg = usageLongitudeDeg;
        this.usageRadiusMeters = usageRadiusMeters;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.ruleVersionId = ruleVersionId;
        if (status == LicenseStatus.ACTIVE) {
            this.startedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getLicenseNo() {
        return licenseNo;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getStationId() {
        return stationId;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public long getBandLowHz() {
        return bandLowHz;
    }

    public void setBandLowHz(long bandLowHz) {
        this.bandLowHz = bandLowHz;
    }

    public long getBandHighHz() {
        return bandHighHz;
    }

    public void setBandHighHz(long bandHighHz) {
        this.bandHighHz = bandHighHz;
    }

    public double getUsageLatitudeDeg() {
        return usageLatitudeDeg;
    }

    public double getUsageLongitudeDeg() {
        return usageLongitudeDeg;
    }

    public double getUsageRadiusMeters() {
        return usageRadiusMeters;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public LicenseStatus getStatus() {
        return status;
    }

    public void setStatus(LicenseStatus status) {
        this.status = status;
    }

    public Long getRuleVersionId() {
        return ruleVersionId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public Instant getTerminatedAt() {
        return terminatedAt;
    }

    /** 到达开始时间：NOT_STARTED → ACTIVE。 */
    public void markStarted() {
        this.status = LicenseStatus.ACTIVE;
        this.startedAt = Instant.now();
    }

    public void markPaused() {
        this.status = LicenseStatus.PAUSED;
        this.pausedAt = Instant.now();
    }

    public void markTerminated() {
        this.status = LicenseStatus.TERMINATED;
        this.terminatedAt = Instant.now();
    }
}
