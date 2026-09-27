package com.chris64233.spectrumcoordination.repo;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.spectrumcoordination.domain.IdSequence;

public interface IdSequenceRepository extends JpaRepository<IdSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from IdSequence s where s.name = :name")
    IdSequence lockByName(@Param("name") String name);
}
