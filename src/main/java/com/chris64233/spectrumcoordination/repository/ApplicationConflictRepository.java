package com.chris64233.spectrumcoordination.repository;

import com.chris64233.spectrumcoordination.domain.ApplicationConflict;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationConflictRepository extends JpaRepository<ApplicationConflict, Long> {

    List<ApplicationConflict> findByApplicationId(Long applicationId);

    void deleteByApplicationId(Long applicationId);
}
