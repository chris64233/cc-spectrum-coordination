package com.chris64233.spectrumcoordination.repository;

import com.chris64233.spectrumcoordination.domain.Station;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StationRepository extends JpaRepository<Station, Long> {
}
