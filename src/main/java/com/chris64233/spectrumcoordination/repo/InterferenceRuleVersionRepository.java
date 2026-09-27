package com.chris64233.spectrumcoordination.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.chris64233.spectrumcoordination.domain.InterferenceRuleVersion;
import com.chris64233.spectrumcoordination.domain.RuleVersionStatus;

public interface InterferenceRuleVersionRepository extends JpaRepository<InterferenceRuleVersion, Long> {

    Optional<InterferenceRuleVersion> findByStatus(RuleVersionStatus status);

    Optional<InterferenceRuleVersion> findByVersionNumber(int versionNumber);

    boolean existsByVersionNumber(int versionNumber);

    @Query("select coalesce(max(r.versionNumber), 0) from InterferenceRuleVersion r")
    int findMaxVersionNumber();
}
