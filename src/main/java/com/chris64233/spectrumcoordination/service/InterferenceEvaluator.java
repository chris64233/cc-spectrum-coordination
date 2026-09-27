package com.chris64233.spectrumcoordination.service;

import com.chris64233.spectrumcoordination.domain.FrequencyOccupancy;
import com.chris64233.spectrumcoordination.domain.FrequencyResource;
import com.chris64233.spectrumcoordination.domain.RuleVersion;
import com.chris64233.spectrumcoordination.domain.Station;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 干扰评估：判定候选占用与现有占用在<b>时间、频段、干扰区域</b>三个维度是否同时重叠，
 * 并按频率资源的最大同频使用规则给出是否违规。
 *
 * <p>三个维度同时满足才算同频干扰：
 * <ol>
 *   <li>时间：[start,end) 与占用 [start,end) 相交；</li>
 *   <li>频率：[low,high) 与占用频段相交（全段判定，不切分批准）；</li>
 *   <li>区域：双方"覆盖圆 + 规则版本干扰余量"扩展圆相交。</li>
 * </ol>
 * 若与候选冲突的占用数 + 候选自身 &gt; 资源允许的 maxCoChannelUsers，则违规。
 */
@Component
public class InterferenceEvaluator {

    /** 一次重叠事实。 */
    public record Overlap(long licenseId, long stationId, double overlapMeters) {
    }

    /** 候选占用参数（申请或许可改频的通用表示）。 */
    public record Candidate(long resourceId,
                            long bandLowHz, long bandHighHz,
                            double latDeg, double lonDeg, double radiusMeters,
                            double powerWatts,
                            Instant startTime, Instant endTime) {

        public static Candidate fromStation(Station station, long resourceId,
                                            long bandLowHz, long bandHighHz,
                                            Double latDeg, Double lonDeg, Double radiusMeters,
                                            Instant startTime, Instant endTime) {
            return new Candidate(resourceId, bandLowHz, bandHighHz,
                    latDeg == null ? station.getLatitudeDeg() : latDeg,
                    lonDeg == null ? station.getLongitudeDeg() : lonDeg,
                    radiusMeters == null ? station.getRadiusMeters() : radiusMeters,
                    station.getPowerWatts(), startTime, endTime);
        }
    }

    /**
     * 找出所有三重重叠的现有占用。
     *
     * @param excludeLicenseIds 改频时排除自身许可占用
     */
    public List<Overlap> findOverlaps(Candidate candidate, RuleVersion rule,
                                      List<FrequencyOccupancy> existing,
                                      Set<Long> excludeLicenseIds) {
        List<Overlap> overlaps = new ArrayList<>();
        double candidateExtendedRadius =
                candidate.radiusMeters() + rule.interferenceAllowanceMeters(candidate.powerWatts());

        for (FrequencyOccupancy occ : existing) {
            if (excludeLicenseIds.contains(occ.getLicenseId())) {
                continue;
            }
            if (!timeOverlap(candidate, occ) || !bandOverlap(candidate, occ)) {
                continue;
            }
            double existingExtendedRadius =
                    occ.getUsageRadiusMeters() + rule.interferenceAllowanceMeters(occ.getPowerWatts());
            double overlap = GeoCalculator.circleOverlapMeters(
                    candidate.latDeg(), candidate.lonDeg(), candidateExtendedRadius,
                    occ.getUsageLatitudeDeg(), occ.getUsageLongitudeDeg(), existingExtendedRadius);
            if (overlap > 0) {
                overlaps.add(new Overlap(occ.getLicenseId(), occ.getStationId(), overlap));
            }
        }
        return overlaps;
    }

    /**
     * 完整评估：返回冲突列表与是否违反最大同频使用规则。
     */
    public Evaluation evaluate(Candidate candidate, RuleVersion rule, FrequencyResource resource,
                               List<FrequencyOccupancy> existing, Set<Long> excludeLicenseIds) {
        List<Overlap> overlaps = findOverlaps(candidate, rule, existing, excludeLicenseIds);
        boolean allowed = overlaps.size() + 1 <= resource.getMaxCoChannelUsers();
        return new Evaluation(overlaps, allowed);
    }

    private boolean timeOverlap(Candidate c, FrequencyOccupancy o) {
        return c.startTime().isBefore(o.getEndTime()) && c.endTime().isAfter(o.getStartTime());
    }

    private boolean bandOverlap(Candidate c, FrequencyOccupancy o) {
        return c.bandLowHz() < o.getBandHighHz() && c.bandHighHz() > o.getBandLowHz();
    }

    /** 评估结果。allowed=false 时 overlaps 即冲突对象。 */
    public record Evaluation(List<Overlap> overlaps, boolean allowed) {
    }
}
