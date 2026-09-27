package com.chris64233.spectrumcoordination.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 全局干扰协调锁。单行（id = 1）悲观锁，用于串行化"批准 / 改频"判定，
 * 保证相邻区域同一频段的并发申请最终不会出现违反 maxCoChannelUsers 的结果。
 *
 * <p>行锁仅在事务期间持有：事务先获取锁，再读占用、做干扰判定、原子写入整段占用。
 */
@Entity
@Table(name = "coordination_lock")
public class CoordinationLock {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    protected CoordinationLock() {
    }

    public CoordinationLock(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
