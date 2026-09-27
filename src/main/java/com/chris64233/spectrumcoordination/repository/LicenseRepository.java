package com.chris64233.spectrumcoordination.repository;

import com.chris64233.spectrumcoordination.domain.License;
import com.chris64233.spectrumcoordination.domain.LicenseStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LicenseRepository extends JpaRepository<License, Long> {

    Optional<License> findByLicenseNo(String licenseNo);

    Optional<License> findByApplicationId(Long applicationId);

    List<License> findByStationId(Long stationId);

    List<License> findByStatus(LicenseStatus status);
}
