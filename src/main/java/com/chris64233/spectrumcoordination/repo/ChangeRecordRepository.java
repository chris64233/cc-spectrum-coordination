package com.chris64233.spectrumcoordination.repo;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.spectrumcoordination.domain.ChangeRecord;

public interface ChangeRecordRepository extends JpaRepository<ChangeRecord, Long> {

    @Query("select c from ChangeRecord c where c.licenseNo = :licenseNo order by c.changedAt desc, c.id desc")
    List<ChangeRecord> findByLicenseNo(@Param("licenseNo") String licenseNo);

    @Query("select c from ChangeRecord c where c.applicationNo = :applicationNo order by c.changedAt desc, c.id desc")
    List<ChangeRecord> findByApplicationNo(@Param("applicationNo") String applicationNo);

    @Query("select c from ChangeRecord c order by c.changedAt desc, c.id desc")
    List<ChangeRecord> findLatest(Pageable pageable);
}
