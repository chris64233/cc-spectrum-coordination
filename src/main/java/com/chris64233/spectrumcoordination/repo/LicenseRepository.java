package com.chris64233.spectrumcoordination.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.spectrumcoordination.domain.License;
import com.chris64233.spectrumcoordination.domain.LicenseStatus;

public interface LicenseRepository extends JpaRepository<License, Long> {

    Optional<License> findByLicenseNo(String licenseNo);

    List<License> findByStatus(LicenseStatus status);
}
