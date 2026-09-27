package com.chris64233.spectrumcoordination.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 频率占用：许可在“频段 × 时间窗 × 地理圆”上的占用事实，是冲突判定的唯一数据源。
 *
 * <p>批准时以申请的整段频段、整段时间写入，禁止部分批准；改频时在同一事务内
 * 插入新行、删除旧行；提前终止时截断 {@code endTime}。
 */
@Entity
@Table(name = "frequency_occupancy")
public class FrequencyOccupancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "license_id", nullable = false)
    private License license;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private FrequencyResource resource;

    @Column(nullable = false)
    private double bandStartMhz;

    @Column(nullable = false)
    private double bandEndMhz;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private double radiusKm;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    protected FrequencyOccupancy() {
    }

    public FrequencyOccupancy(License license, FrequencyResource resource,
                              double bandStartMhz, double bandEndMhz,
                              double latitude, double longitude, double radiusKm,
                              Instant startTime, Instant endTime) {
        this.license = license;
        this.resource = resource;
        this.bandStartMhz = bandStartMhz;
        this.bandEndMhz = bandEndMhz;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusKm = radiusKm;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getId() {
        return id;
    }

    public License getLicense() {
        return license;
    }

    public FrequencyResource getResource() {
        return resource;
    }

    public void setResource(FrequencyResource resource) {
        this.resource = resource;
    }

    public double getBandStartMhz() {
        return bandStartMhz;
    }

    public void setBandStartMhz(double bandStartMhz) {
        this.bandStartMhz = bandStartMhz;
    }

    public double getBandEndMhz() {
        return bandEndMhz;
    }

    public void setBandEndMhz(double bandEndMhz) {
        this.bandEndMhz = bandEndMhz;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getRadiusKm() {
        return radiusKm;
    }

    public void setRadiusKm(double radiusKm) {
        this.radiusKm = radiusKm;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }
}
