package com.chris64233.spectrumcoordination.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.spectrumcoordination.domain.FrequencyApplication;

public interface FrequencyApplicationRepository extends JpaRepository<FrequencyApplication, Long> {

    Optional<FrequencyApplication> findByApplicationNo(String applicationNo);

    boolean existsByApplicationNo(String applicationNo);
}
