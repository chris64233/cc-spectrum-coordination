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

/**
 * 频率使用许可申请。
 *
 * <p>申请号（{@code applicationNo}）全局唯一，作为幂等键：同一申请号重复提交直接返回
 * 既有结果。申请记录提交时的规则版本，批准时若规则版本已升级则按新版本重新校验。
 */
@Entity
@Table(name = "frequency_application")
public class FrequencyApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 申请号，幂等键 */
    @Column(nullable = false, unique = true, length = 64)
    private String applicationNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private FrequencyResource resource;

    /** 所需频段起点（MHz，左闭右开），必须落在频率资源频段内 */
    @Column(nullable = false)
    private double bandStartMhz;

    @Column(nullable = false)
    private double bandEndMhz;

    /** 使用区域圆心纬度 */
    @Column(nullable = false)
    private double useLatitude;

    @Column(nullable = false)
    private double useLongitude;

    /** 使用区域半径（千米） */
    @Column(nullable = false)
    private double useRadiusKm;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApplicationStatus status;

    /** 提交时生效的规则版本 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_rule_version_id", nullable = false)
    private InterferenceRuleVersion submittedRuleVersion;

    /** 批准/拒绝时实际使用的规则版本（重新校验后可能与提交版本不同） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_rule_version_id")
    private InterferenceRuleVersion decisionRuleVersion;

    /** 批准后生成的许可 ID */
    @Column(name = "license_id")
    private Long licenseId;

    /** 决定原因，如冲突对象摘要 */
    @Column(length = 2000)
    private String decisionReason;

    @Column(nullable = false)
    private Instant submittedAt;

    private Instant decidedAt;

    protected FrequencyApplication() {
    }

    public FrequencyApplication(String applicationNo, Station station, FrequencyResource resource,
                                double bandStartMhz, double bandEndMhz,
                                double useLatitude, double useLongitude, double useRadiusKm,
                                Instant startTime, Instant endTime,
                                InterferenceRuleVersion submittedRuleVersion, Instant submittedAt) {
        this.applicationNo = applicationNo;
        this.station = station;
        this.resource = resource;
        this.bandStartMhz = bandStartMhz;
        this.bandEndMhz = bandEndMhz;
        this.useLatitude = useLatitude;
        this.useLongitude = useLongitude;
        this.useRadiusKm = resolveUseRadius(useRadiusKm, station);
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = ApplicationStatus.PENDING;
        this.submittedRuleVersion = submittedRuleVersion;
        this.submittedAt = submittedAt;
    }

    private static double resolveUseRadius(double useRadiusKm, Station station) {
        return useRadiusKm > 0 ? useRadiusKm : station.getCoverageRadiusKm();
    }

    public Long getId() {
        return id;
    }

    public String getApplicationNo() {
        return applicationNo;
    }

    public Station getStation() {
        return station;
    }

    public FrequencyResource getResource() {
        return resource;
    }

    public double getBandStartMhz() {
        return bandStartMhz;
    }

    public double getBandEndMhz() {
        return bandEndMhz;
    }

    public double getUseLatitude() {
        return useLatitude;
    }

    public double getUseLongitude() {
        return useLongitude;
    }

    public double getUseRadiusKm() {
        return useRadiusKm;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public InterferenceRuleVersion getSubmittedRuleVersion() {
        return submittedRuleVersion;
    }

    public InterferenceRuleVersion getDecisionRuleVersion() {
        return decisionRuleVersion;
    }

    public void setDecisionRuleVersion(InterferenceRuleVersion decisionRuleVersion) {
        this.decisionRuleVersion = decisionRuleVersion;
    }

    public Long getLicenseId() {
        return licenseId;
    }

    public void setLicenseId(Long licenseId) {
        this.licenseId = licenseId;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
