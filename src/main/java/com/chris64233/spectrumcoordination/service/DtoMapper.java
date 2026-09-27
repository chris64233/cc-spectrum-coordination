package com.chris64233.spectrumcoordination.service;

import java.time.Clock;

import com.chris64233.spectrumcoordination.domain.ChangeRecord;
import com.chris64233.spectrumcoordination.domain.FrequencyApplication;
import com.chris64233.spectrumcoordination.domain.FrequencyOccupancy;
import com.chris64233.spectrumcoordination.domain.FrequencyResource;
import com.chris64233.spectrumcoordination.domain.InterferenceRuleVersion;
import com.chris64233.spectrumcoordination.domain.License;
import com.chris64233.spectrumcoordination.domain.LicenseStatus;
import com.chris64233.spectrumcoordination.domain.Station;
import com.chris64233.spectrumcoordination.dto.ApplicationDto;
import com.chris64233.spectrumcoordination.dto.ChangeRecordDto;
import com.chris64233.spectrumcoordination.dto.ConflictDto;
import com.chris64233.spectrumcoordination.dto.FrequencyResourceDto;
import com.chris64233.spectrumcoordination.dto.LicenseDto;
import com.chris64233.spectrumcoordination.dto.RuleVersionDto;
import com.chris64233.spectrumcoordination.dto.StationDto;

/**
 * 实体到 DTO 的统一转换。
 */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static StationDto station(Station s) {
        return new StationDto(s.getId(), s.getStationCode(), s.getName(),
                s.getLatitude(), s.getLongitude(), s.getCoverageRadiusKm(),
                s.getTransmitPowerW(), s.getDeviceType());
    }

    public static FrequencyResourceDto resource(FrequencyResource r) {
        return new FrequencyResourceDto(r.getId(), r.getBandStartMhz(), r.getBandEndMhz(),
                r.getRegionLatitude(), r.getRegionLongitude(), r.getRegionRadiusKm(),
                r.getMaxCoChannelUsers());
    }

    public static RuleVersionDto rule(InterferenceRuleVersion r) {
        return new RuleVersionDto(r.getId(), r.getVersionNumber(), r.getCoChannelReuseDistanceKm(),
                r.getDescription(), r.getStatus(), r.getEffectiveAt());
    }

    public static ConflictDto conflict(InterferenceEngine.Conflict c) {
        FrequencyOccupancy o = c.occupancy();
        return new ConflictDto(c.licenseNo(), c.stationCode(),
                o.getBandStartMhz(), o.getBandEndMhz(),
                o.getLatitude(), o.getLongitude(), o.getRadiusKm(),
                c.distanceKm(), c.requiredSeparationKm(), c.reason());
    }

    public static LicenseDto license(License l, Clock clock) {
        LicenseStatus stored = l.getStatus();
        LicenseStatus effective = stored.effective(clock.instant(), l.getStartTime(), l.getEndTime());
        return new LicenseDto(l.getLicenseNo(), l.getStation().getStationCode(), l.getStation().getName(),
                l.getResource().getId(), l.getBandStartMhz(), l.getBandEndMhz(),
                l.getUseLatitude(), l.getUseLongitude(), l.getUseRadiusKm(),
                l.getOriginalStartTime(), l.getOriginalEndTime(),
                l.getStartTime(), l.getEndTime(),
                stored.name(), effective.name(),
                l.getRuleVersionNumber(), l.getSourceApplicationNo(),
                l.getCreatedAt(), l.getVersion());
    }

    public static ApplicationDto application(FrequencyApplication a, License license, boolean idempotentReplay) {
        return new ApplicationDto(a.getApplicationNo(), a.getStation().getStationCode(),
                a.getResource().getId(), a.getBandStartMhz(), a.getBandEndMhz(),
                a.getUseLatitude(), a.getUseLongitude(), a.getUseRadiusKm(),
                a.getStartTime(), a.getEndTime(), a.getStatus(),
                a.getSubmittedRuleVersion().getVersionNumber(),
                a.getDecisionRuleVersion() == null ? null : a.getDecisionRuleVersion().getVersionNumber(),
                license == null ? null : license.getLicenseNo(),
                a.getDecisionReason(), a.getSubmittedAt(), a.getDecidedAt(), idempotentReplay);
    }

    public static ChangeRecordDto change(ChangeRecord c) {
        return new ChangeRecordDto(c.getId(), c.getLicenseNo(), c.getApplicationNo(),
                c.getChangeType(), c.getFromStatus(), c.getToStatus(),
                c.getRuleVersionNumber(), c.getDetail(), c.getChangedAt());
    }
}
