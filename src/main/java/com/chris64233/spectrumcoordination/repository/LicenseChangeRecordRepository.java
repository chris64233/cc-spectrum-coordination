package com.chris64233.spectrumcoordination.repository;

import com.chris64233.spectrumcoordination.domain.LicenseChangeRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LicenseChangeRecordRepository extends JpaRepository<LicenseChangeRecord, Long> {

    List<LicenseChangeRecord> findByLicenseIdOrderByOccurredAtAsc(Long licenseId);
}
