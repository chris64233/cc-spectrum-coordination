package com.chris64233.spectrumcoordination.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.spectrumcoordination.domain.Station;

public interface StationRepository extends JpaRepository<Station, Long> {
    Optional<Station> findByStationCode(String stationCode);
}
