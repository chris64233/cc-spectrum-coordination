package com.chris64233.spectrumcoordination.web;

import com.chris64233.spectrumcoordination.domain.FrequencyResource;
import com.chris64233.spectrumcoordination.domain.Station;
import com.chris64233.spectrumcoordination.service.CatalogService;
import com.chris64233.spectrumcoordination.web.dto.FrequencyResourceCreateRequest;
import com.chris64233.spectrumcoordination.web.dto.StationCreateRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping("/stations")
    @ResponseStatus(HttpStatus.CREATED)
    public Station createStation(@Valid @RequestBody StationCreateRequest req) {
        return catalogService.createStation(req);
    }

    @GetMapping("/stations")
    public List<Station> listStations() {
        return catalogService.listStations();
    }

    @GetMapping("/stations/{id}")
    public Station getStation(@PathVariable Long id) {
        return catalogService.getStation(id);
    }

    @PostMapping("/frequency-resources")
    @ResponseStatus(HttpStatus.CREATED)
    public FrequencyResource createResource(@Valid @RequestBody FrequencyResourceCreateRequest req) {
        return catalogService.createResource(req);
    }

    @GetMapping("/frequency-resources")
    public List<FrequencyResource> listResources() {
        return catalogService.listResources();
    }

    @GetMapping("/frequency-resources/{id}")
    public FrequencyResource getResource(@PathVariable Long id) {
        return catalogService.getResource(id);
    }
}
