package com.chris64233.spectrumcoordination.repository;

import com.chris64233.spectrumcoordination.domain.FrequencyOccupancy;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FrequencyOccupancyRepository extends JpaRepository<FrequencyOccupancy, Long> {

    List<FrequencyOccupancy> findByResourceId(Long resourceId);

    List<FrequencyOccupancy> findByLicenseId(Long licenseId);

    void deleteByLicenseId(Long licenseId);
}
