package com.chris64233.spectrumcoordination.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 频率资源：记录可申请的频段、适用区域以及允许的最大同频使用数。
 *
 * <p>区域以圆心经纬度 + 半径（千米）描述；{@code maxCoChannelUsers} 表示在
 * 相互构成干扰的一组台站中，同一频段允许同时存在的许可占用数量上限，
 * 1 表示同一干扰区域内同频仅允许一个占用（完全排他）。
 */
@Entity
@Table(name = "frequency_resource")
public class FrequencyResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 频段起始频率，MHz，区间采用左闭右开 */
    @Column(nullable = false)
    private double bandStartMhz;

    /** 频段结束频率，MHz（不含） */
    @Column(nullable = false)
    private double bandEndMhz;

    /** 区域中心纬度 */
    @Column(nullable = false)
    private double regionLatitude;

    /** 区域中心经度 */
    @Column(nullable = false)
    private double regionLongitude;

    /** 区域半径，千米 */
    @Column(nullable = false)
    private double regionRadiusKm;

    @Column(nullable = false)
    private int maxCoChannelUsers;

    protected FrequencyResource() {
    }

    public FrequencyResource(double bandStartMhz, double bandEndMhz,
                             double regionLatitude, double regionLongitude, double regionRadiusKm,
                             int maxCoChannelUsers) {
        this.bandStartMhz = bandStartMhz;
        this.bandEndMhz = bandEndMhz;
        this.regionLatitude = regionLatitude;
        this.regionLongitude = regionLongitude;
        this.regionRadiusKm = regionRadiusKm;
        this.maxCoChannelUsers = maxCoChannelUsers;
    }

    public Long getId() {
        return id;
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

    public double getRegionLatitude() {
        return regionLatitude;
    }

    public void setRegionLatitude(double regionLatitude) {
        this.regionLatitude = regionLatitude;
    }

    public double getRegionLongitude() {
        return regionLongitude;
    }

    public void setRegionLongitude(double regionLongitude) {
        this.regionLongitude = regionLongitude;
    }

    public double getRegionRadiusKm() {
        return regionRadiusKm;
    }

    public void setRegionRadiusKm(double regionRadiusKm) {
        this.regionRadiusKm = regionRadiusKm;
    }

    public int getMaxCoChannelUsers() {
        return maxCoChannelUsers;
    }

    public void setMaxCoChannelUsers(int maxCoChannelUsers) {
        this.maxCoChannelUsers = maxCoChannelUsers;
    }
}
