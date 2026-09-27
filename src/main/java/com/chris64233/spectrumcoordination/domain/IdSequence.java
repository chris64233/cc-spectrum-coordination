package com.chris64233.spectrumcoordination.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 业务编号序列（如许可编号），行锁递增，保证并发批准时编号不重复。
 */
@Entity
@Table(name = "id_sequence")
public class IdSequence {

    @Id
    @Column(length = 32)
    private String name;

    @Column(nullable = false)
    private long nextValue;

    protected IdSequence() {
    }

    public IdSequence(String name, long nextValue) {
        this.name = name;
        this.nextValue = nextValue;
    }

    public String getName() {
        return name;
    }

    public long getNextValue() {
        return nextValue;
    }

    public void setNextValue(long nextValue) {
        this.nextValue = nextValue;
    }
}
