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
 * 频率使用许可申请。
 *
 * <p>{@code applicationNo} 唯一，保证同一申请号重复提交幂等。申请记录提交时所依据的
 * 规则版本；批准时若该版本已不是生效版本，则按新规则重新校验。
 */
@Entity
@Table(name = "frequency_application")
public class FrequencyApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String applicationNo;

    @Column(nullable = false)
    private Long stationId;

    @Column(nullable = false)
    private Long resourceId;

    /** 申请频段下界（Hz，含），必须落在频率资源频段内。 */
    @Column(nullable = false)
    private long bandLowHz;

    /** 申请频段上界（Hz，不含）。批准为全有或全无，不允许只批准其中一段。 */
    @Column(nullable = false)
    private long bandHighHz;

    /** 使用区域中心纬度（度），默认取台站位置。 */
    @Column(nullable = false)
    private double usageLatitudeDeg;

    @Column(nullable = false)
    private double usageLongitudeDeg;

    /** 使用区域半径（米），默认取台站覆盖半径。 */
    @Column(nullable = false)
    private double usageRadiusMeters;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    /** 提交时所依据的规则版本。 */
    @Column(nullable = false)
    private Long ruleVersionId;

    /** 最近一次批准/重校验实际使用的规则版本。 */
    private Long validatedRuleVersionId;

    @Column(nullable = false)
    private Instant submittedAt = Instant.now();

    private Instant decidedAt;

    protected FrequencyApplication() {
    }

    public FrequencyApplication(String applicationNo, Long stationId, Long resourceId,
                                long bandLowHz, long bandHighHz,
                                double usageLatitudeDeg, double usageLongitudeDeg,
                                double usageRadiusMeters,
                                Instant startTime, Instant endTime, Long ruleVersionId) {
        this.applicationNo = applicationNo;
        this.stationId = stationId;
        this.resourceId = resourceId;
        this.bandLowHz = bandLowHz;
        this.bandHighHz = bandHighHz;
        this.usageLatitudeDeg = usageLatitudeDeg;
        this.usageLongitudeDeg = usageLongitudeDeg;
        this.usageRadiusMeters = usageRadiusMeters;
        this.startTime = startTime;
        this.endTime = endTime;
        this.ruleVersionId = ruleVersionId;
    }

    public Long getId() {
        return id;
    }

    public String getApplicationNo() {
        return applicationNo;
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

    public Long getRuleVersionId() {
        return ruleVersionId;
    }

    public Long getValidatedRuleVersionId() {
        return validatedRuleVersionId;
    }

    public void setValidatedRuleVersionId(Long validatedRuleVersionId) {
        this.validatedRuleVersionId = validatedRuleVersionId;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void markDecided() {
        this.decidedAt = Instant.now();
    }
}
