package com.chris64233.spectrumcoordination.repo;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.spectrumcoordination.domain.FrequencyResource;

public interface FrequencyResourceRepository extends JpaRepository<FrequencyResource, Long> {

    /**
     * 悲观行锁：申请批准、改频等冲突裁决事务先锁住目标频率资源行，
     * 使同一资源上的并发裁决串行化，避免双批准违反干扰规则。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FrequencyResource r where r.id = :id")
    FrequencyResource lockById(@Param("id") Long id);
}
