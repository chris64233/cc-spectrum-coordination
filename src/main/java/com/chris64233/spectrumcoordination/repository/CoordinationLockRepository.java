package com.chris64233.spectrumcoordination.repository;

import com.chris64233.spectrumcoordination.domain.CoordinationLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface CoordinationLockRepository extends JpaRepository<CoordinationLock, Long> {

    /**
     * 阻塞式获取全局干扰协调锁（单行悲观写锁）。
     * 必须在事务内调用；并发的批准/改频在此串行，保证三重重叠判定与占用写入之间
     * 不存在竞态窗口。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from CoordinationLock l where l.id = 1")
    CoordinationLock acquireForCoordination();
}
