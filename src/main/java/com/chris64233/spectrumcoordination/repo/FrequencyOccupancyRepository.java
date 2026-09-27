package com.chris64233.spectrumcoordination.repo;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.spectrumcoordination.domain.FrequencyOccupancy;

public interface FrequencyOccupancyRepository extends JpaRepository<FrequencyOccupancy, Long> {

    List<FrequencyOccupancy> findByLicenseId(Long licenseId);

    void deleteByLicenseId(Long licenseId);

    /**
     * 粗筛与给定时间窗、频段均重叠、且状态可能仍占用未来时间的占用记录。
     * 频段采用左闭右开；时间窗采用半开区间（[start,end)），刚好首尾相接不算冲突。
     * 地理圆相交与状态判定在服务层完成。
     */
    @Query("""
            select o from FrequencyOccupancy o
            where o.resource.id = :resourceId
              and o.bandStartMhz < :bandEnd
              and o.bandEndMhz > :bandStart
              and o.startTime < :endTime
              and o.endTime > :startTime
            """)
    List<FrequencyOccupancy> findOverlapCandidates(@Param("resourceId") Long resourceId,
                                                   @Param("bandStart") double bandStart,
                                                   @Param("bandEnd") double bandEnd,
                                                   @Param("startTime") Instant startTime,
                                                   @Param("endTime") Instant endTime);
}
