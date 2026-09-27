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
 * 频率占用：一个已发放许可对整段申请频段的占用，是干扰校验的事实来源。
 *
 * <p>仅在许可处于未开始/已开始（占用中）状态时存在；暂停或终止时删除对应行，
 * 许可本身保留。改频时新占用提交成功后才删除旧占用（同一事务）。
 */
@Entity
@Table(name = "frequency_occupancy", indexes = {
        @Index(name = "idx_occ_resource", columnList = "resourceId"),
        @Index(name = "idx_occ_license", columnList = "licenseId")
})
public class FrequencyOccupancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long licenseId;

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

    /** 申请台站发射功率（瓦），干扰计算时使用。 */
    @Column(nullable = false)
    private double powerWatts;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    protected FrequencyOccupancy() {
    }

    public FrequencyOccupancy(Long licenseId, Long stationId, Long resourceId,
                              long bandLowHz, long bandHighHz,
                              double usageLatitudeDeg, double usageLongitudeDeg,
                              double usageRadiusMeters, double powerWatts,
                              Instant startTime, Instant endTime) {
        this.licenseId = licenseId;
        this.stationId = stationId;
        this.resourceId = resourceId;
        this.bandLowHz = bandLowHz;
        this.bandHighHz = bandHighHz;
        this.usageLatitudeDeg = usageLatitudeDeg;
        this.usageLongitudeDeg = usageLongitudeDeg;
        this.usageRadiusMeters = usageRadiusMeters;
        this.powerWatts = powerWatts;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getId() {
        return id;
    }

    public Long getLicenseId() {
        return licenseId;
    }

    public Long getStationId() {
        return stationId;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public long getBandLowHz() {
        return bandLowHz;
    }

    public long getBandHighHz() {
        return bandHighHz;
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

    public double getPowerWatts() {
        return powerWatts;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }
}
