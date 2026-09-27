package com.chris64233.spectrumcoordination.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 频率使用许可。
 *
 * <p>许可对应一次“整段频率 + 整段时间”的原子批准。许可状态结合当前时间折算有效状态
 * （见 {@link LicenseStatus#effective}）。改频通过生成新占用、原子删除旧占用实现；
 * 暂停不释放占用（保障恢复）；提前终止会截断占用的时间尾段并保留历史记录。
 */
@Entity
@Table(name = "license")
public class License {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 许可编号，业务唯一 */
    @Column(nullable = false, unique = true, length = 64)
    private String licenseNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private FrequencyResource resource;

    @Column(nullable = false)
    private double bandStartMhz;

    @Column(nullable = false)
    private double bandEndMhz;

    @Column(nullable = false)
    private double useLatitude;

    @Column(nullable = false)
    private double useLongitude;

    @Column(nullable = false)
    private double useRadiusKm;

    /** 原始批准的时间窗起点，终身不变，供历史查询 */
    @Column(nullable = false)
    private Instant originalStartTime;

    @Column(nullable = false)
    private Instant originalEndTime;

    /** 当前有效时间窗（终止时会被截断到终止时刻） */
    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LicenseStatus status;

    /** 批准时依据的规则版本号（冗余存储，便于直接查询） */
    @Column(nullable = false)
    private int ruleVersionNumber;

    /** 来源申请号，便于追溯 */
    @Column(nullable = false, length = 64)
    private String sourceApplicationNo;

    @Column(nullable = false)
    private Instant createdAt;

    /** JPA 乐观锁，保证改频/暂停/终止并发操作的一致性 */
    @Version
    private long version;

    protected License() {
    }

    public License(String licenseNo, Station station, FrequencyResource resource,
                   double bandStartMhz, double bandEndMhz,
                   double useLatitude, double useLongitude, double useRadiusKm,
                   Instant startTime, Instant endTime, int ruleVersionNumber,
                   String sourceApplicationNo, Instant createdAt) {
        this.licenseNo = licenseNo;
        this.station = station;
        this.resource = resource;
        this.bandStartMhz = bandStartMhz;
        this.bandEndMhz = bandEndMhz;
        this.useLatitude = useLatitude;
        this.useLongitude = useLongitude;
        this.useRadiusKm = useRadiusKm;
        this.originalStartTime = startTime;
        this.originalEndTime = endTime;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = LicenseStatus.NOT_STARTED;
        this.ruleVersionNumber = ruleVersionNumber;
        this.sourceApplicationNo = sourceApplicationNo;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getLicenseNo() {
        return licenseNo;
    }

    public Station getStation() {
        return station;
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

    public double getUseLatitude() {
        return useLatitude;
    }

    public void setUseLatitude(double useLatitude) {
        this.useLatitude = useLatitude;
    }

    public double getUseLongitude() {
        return useLongitude;
    }

    public void setUseLongitude(double useLongitude) {
        this.useLongitude = useLongitude;
    }

    public double getUseRadiusKm() {
        return useRadiusKm;
    }

    public void setUseRadiusKm(double useRadiusKm) {
        this.useRadiusKm = useRadiusKm;
    }

    public Instant getOriginalStartTime() {
        return originalStartTime;
    }

    public Instant getOriginalEndTime() {
        return originalEndTime;
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

    public LicenseStatus getStatus() {
        return status;
    }

    public void setStatus(LicenseStatus status) {
        this.status = status;
    }

    public int getRuleVersionNumber() {
        return ruleVersionNumber;
    }

    public String getSourceApplicationNo() {
        return sourceApplicationNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
