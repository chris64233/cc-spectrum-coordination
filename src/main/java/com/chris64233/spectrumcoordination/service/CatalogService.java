package com.chris64233.spectrumcoordination.service;

import com.chris64233.spectrumcoordination.domain.FrequencyResource;
import com.chris64233.spectrumcoordination.domain.Station;
import com.chris64233.spectrumcoordination.repository.FrequencyResourceRepository;
import com.chris64233.spectrumcoordination.repository.StationRepository;
import com.chris64233.spectrumcoordination.web.dto.FrequencyResourceCreateRequest;
import com.chris64233.spectrumcoordination.web.dto.StationCreateRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 台站与频率资源目录管理。 */
@Service
public class CatalogService {

    private final StationRepository stationRepository;
    private final FrequencyResourceRepository resourceRepository;

    public CatalogService(StationRepository stationRepository,
                          FrequencyResourceRepository resourceRepository) {
        this.stationRepository = stationRepository;
        this.resourceRepository = resourceRepository;
    }

    @Transactional
    public Station createStation(StationCreateRequest req) {
        return stationRepository.save(new Station(
                req.name(), req.latitudeDeg(), req.longitudeDeg(),
                req.radiusMeters(), req.powerWatts(), req.deviceType()));
    }

    @Transactional(readOnly = true)
    public List<Station> listStations() {
        return stationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Station getStation(Long id) {
        return stationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("台站不存在: " + id));
    }

    @Transactional
    public FrequencyResource createResource(FrequencyResourceCreateRequest req) {
        if (req.bandLowHz() >= req.bandHighHz()) {
            throw new BusinessRuleException("频段下界必须小于上界");
        }
        return resourceRepository.save(new FrequencyResource(
                req.name(), req.bandLowHz(), req.bandHighHz(),
                req.regionLatitudeDeg(), req.regionLongitudeDeg(),
                req.regionRadiusMeters(), req.maxCoChannelUsers()));
    }

    @Transactional(readOnly = true)
    public List<FrequencyResource> listResources() {
        return resourceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public FrequencyResource getResource(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("频率资源不存在: " + id));
    }
}
