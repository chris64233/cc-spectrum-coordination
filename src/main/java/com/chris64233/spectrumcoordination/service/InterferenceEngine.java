package com.chris64233.spectrumcoordination.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.chris64233.spectrumcoordination.domain.FrequencyOccupancy;
import com.chris64233.spectrumcoordination.domain.FrequencyResource;
import com.chris64233.spectrumcoordination.domain.InterferenceRuleVersion;

/**
 * 干扰判定引擎（无状态）。
 *
 * <p>判定两个占用是否构成同频干扰，需同时满足：
 * <ol>
 *   <li>频段重叠（左闭右开区间相交）；</li>
 *   <li>时间窗重叠（半开区间相交，首尾相接不算重叠）；</li>
 *   <li>干扰区域重叠：两个使用圆的圆心距不大于
 *       {@code 半径A + 半径B + 规则版本同频复用保护距离}。</li>
 * </ol>
 *
 * <p>容量规则：以候选使用圆为基准统计构成干扰的现有占用数量，加上本次候选后
 * 不得超过频率资源的 {@code maxCoChannelUsers}（1 表示同频完全排他）。
 */
public final class InterferenceEngine {

    private InterferenceEngine() {
    }

    /** 待裁决的候选占用（申请整段频段 / 改频目标频段）。 */
    public record Candidate(FrequencyResource resource,
                            double bandStartMhz, double bandEndMhz,
                            double latitude, double longitude, double radiusKm,
                            Instant startTime, Instant endTime) {
    }

    /** 单个冲突对象的判定明细。 */
    public record Conflict(FrequencyOccupancy occupancy,
                           String licenseNo,
                           String stationCode,
                           double distanceKm,
                           double requiredSeparationKm,
                           String reason) {
    }

    /** 裁决结果。 */
    public record CheckResult(boolean allowed,
                              int interferingCount,
                              int maxCoChannelUsers,
                              int ruleVersionNumber,
                              List<Conflict> conflicts) {
    }

    public static boolean bandOverlaps(double s1, double e1, double s2, double e2) {
        return s1 < e2 && s2 < e1;
    }

    public static boolean timeOverlaps(Instant s1, Instant e1, Instant s2, Instant e2) {
        return s1.isBefore(e2) && s2.isBefore(e1);
    }

    /**
     * @param occupants       与候选在频段、时间粗筛重叠的现有占用
     * @param rule            裁决依据的规则版本
     * @param excludeLicenseId 改频/本许可自查时排除的许可 ID，可为 null
     */
    public static CheckResult check(Candidate candidate,
                                    List<FrequencyOccupancy> occupants,
                                    InterferenceRuleVersion rule,
                                    Long excludeLicenseId) {
        List<Conflict> conflicts = new ArrayList<>();
        for (FrequencyOccupancy o : occupants) {
            if (excludeLicenseId != null && o.getLicense().getId().equals(excludeLicenseId)) {
                continue;
            }
            // 频段/时间粗筛后再做一次精确判断（区间均为半开）
            if (!bandOverlaps(candidate.bandStartMhz(), candidate.bandEndMhz(),
                    o.getBandStartMhz(), o.getBandEndMhz())
                    || !timeOverlaps(candidate.startTime(), candidate.endTime(),
                    o.getStartTime(), o.getEndTime())) {
                continue;
            }
            double distance = GeoUtils.haversineKm(
                    candidate.latitude(), candidate.longitude(),
                    o.getLatitude(), o.getLongitude());
            double requiredSeparation = candidate.radiusKm() + o.getRadiusKm()
                    + rule.getCoChannelReuseDistanceKm();
            if (distance <= requiredSeparation) {
                conflicts.add(new Conflict(
                        o,
                        o.getLicense().getLicenseNo(),
                        o.getLicense().getStation().getStationCode(),
                        round(distance),
                        round(requiredSeparation),
                        "同频占用且使用圆相交（含保护距离 %.2f km）".formatted(rule.getCoChannelReuseDistanceKm())));
            }
        }
        int max = candidate.resource().getMaxCoChannelUsers();
        boolean allowed = conflicts.size() < max;
        return new CheckResult(allowed, conflicts.size(), max, rule.getVersionNumber(), conflicts);
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
