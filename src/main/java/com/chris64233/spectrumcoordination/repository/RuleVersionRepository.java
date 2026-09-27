package com.chris64233.spectrumcoordination.repository;

import com.chris64233.spectrumcoordination.domain.RuleVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleVersionRepository extends JpaRepository<RuleVersion, Long> {

    Optional<RuleVersion> findByActiveTrue();

    List<RuleVersion> findAllByOrderByCreatedAtDesc();
}
