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
 * 无线电台站。
 *
 * @param latitudeDeg  纬度（度，WGS-84）
 * @param longitudeDeg 经度（度，WGS-84）
 * @param radiusMeters 覆盖半径（米）
 * @param powerWatts   发射功率（瓦）
 */
@Entity
@Table(name = "station")
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private double latitudeDeg;

    @Column(nullable = false)
    private double longitudeDeg;

    @Column(nullable = false)
    private double radiusMeters;

    @Column(nullable = false)
    private double powerWatts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeviceType deviceType;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Station() {
    }

    public Station(String name, double latitudeDeg, double longitudeDeg, double radiusMeters,
                   double powerWatts, DeviceType deviceType) {
        this.name = name;
        this.latitudeDeg = latitudeDeg;
        this.longitudeDeg = longitudeDeg;
        this.radiusMeters = radiusMeters;
        this.powerWatts = powerWatts;
        this.deviceType = deviceType;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getLatitudeDeg() {
        return latitudeDeg;
    }

    public double getLongitudeDeg() {
        return longitudeDeg;
    }

    public double getRadiusMeters() {
        return radiusMeters;
    }

    public double getPowerWatts() {
        return powerWatts;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
