package com.chris64233.spectrumcoordination.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 无线电台站：记录位置、覆盖半径、发射功率和设备类型。
 */
@Entity
@Table(name = "station")
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String stationCode;

    @Column(nullable = false)
    private String name;

    /** 纬度（度，WGS84 球面近似） */
    @Column(nullable = false)
    private double latitude;

    /** 经度（度） */
    @Column(nullable = false)
    private double longitude;

    /** 覆盖半径，千米 */
    @Column(nullable = false)
    private double coverageRadiusKm;

    /** 发射功率，瓦 */
    @Column(nullable = false)
    private double transmitPowerW;

    /** 设备类型 */
    @Column(nullable = false)
    private String deviceType;

    protected Station() {
    }

    public Station(String stationCode, String name, double latitude, double longitude,
                   double coverageRadiusKm, double transmitPowerW, String deviceType) {
        this.stationCode = stationCode;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.coverageRadiusKm = coverageRadiusKm;
        this.transmitPowerW = transmitPowerW;
        this.deviceType = deviceType;
    }

    public Long getId() {
        return id;
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public double getCoverageRadiusKm() {
        return coverageRadiusKm;
    }

    public void setCoverageRadiusKm(double coverageRadiusKm) {
        this.coverageRadiusKm = coverageRadiusKm;
    }

    public double getTransmitPowerW() {
        return transmitPowerW;
    }

    public void setTransmitPowerW(double transmitPowerW) {
        this.transmitPowerW = transmitPowerW;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }
}
