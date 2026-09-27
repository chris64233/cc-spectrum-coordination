package com.chris64233.spectrumcoordination.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 频率资源：一个可被申请的频段及其所属区域。
 *
 * @param bandLowHz  频段下界（Hz，含）
 * @param bandHighHz 频段上界（Hz，不含）
 * @param maxCoChannelUsers 允许的最大同频使用数：时间、频段、干扰区域三者同时重叠的
 *                          占用总数不得超过该值（1 表示同频排他）
 */
@Entity
@Table(name = "frequency_resource")
public class FrequencyResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private long bandLowHz;

    @Column(nullable = false)
    private long bandHighHz;

    /** 区域中心纬度（度）。 */
    @Column(nullable = false)
    private double regionLatitudeDeg;

    /** 区域中心经度（度）。 */
    @Column(nullable = false)
    private double regionLongitudeDeg;

    /** 区域半径（米）：申请使用区域必须落在该区域内。 */
    @Column(nullable = false)
    private double regionRadiusMeters;

    @Column(nullable = false)
    private int maxCoChannelUsers;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected FrequencyResource() {
    }

    public FrequencyResource(String name, long bandLowHz, long bandHighHz,
                             double regionLatitudeDeg, double regionLongitudeDeg,
                             double regionRadiusMeters, int maxCoChannelUsers) {
        this.name = name;
        this.bandLowHz = bandLowHz;
        this.bandHighHz = bandHighHz;
        this.regionLatitudeDeg = regionLatitudeDeg;
        this.regionLongitudeDeg = regionLongitudeDeg;
        this.regionRadiusMeters = regionRadiusMeters;
        this.maxCoChannelUsers = maxCoChannelUsers;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getBandLowHz() {
        return bandLowHz;
    }

    public long getBandHighHz() {
        return bandHighHz;
    }

    public double getRegionLatitudeDeg() {
        return regionLatitudeDeg;
    }

    public double getRegionLongitudeDeg() {
        return regionLongitudeDeg;
    }

    public double getRegionRadiusMeters() {
        return regionRadiusMeters;
    }

    public int getMaxCoChannelUsers() {
        return maxCoChannelUsers;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
