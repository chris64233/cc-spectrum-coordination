package com.chris64233.spectrumcoordination.repository;

import com.chris64233.spectrumcoordination.domain.FrequencyApplication;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FrequencyApplicationRepository extends JpaRepository<FrequencyApplication, Long> {

    Optional<FrequencyApplication> findByApplicationNo(String applicationNo);

    boolean existsByApplicationNo(String applicationNo);
}
