package com.chris64233.spectrumcoordination.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.spectrumcoordination.service.DtoMapper;
import com.chris64233.spectrumcoordination.service.SpectrumService;
import com.chris64233.spectrumcoordination.dto.FrequencyResourceDto;
import com.chris64233.spectrumcoordination.dto.FrequencyResourceRequest;
import com.chris64233.spectrumcoordination.dto.RuleVersionDto;
import com.chris64233.spectrumcoordination.dto.RuleVersionRequest;
import com.chris64233.spectrumcoordination.dto.StationDto;
import com.chris64233.spectrumcoordination.dto.StationRequest;

/**
 * 基础数据接口：台站、频率资源、干扰规则版本。
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final SpectrumService service;

    public CatalogController(SpectrumService service) {
        this.service = service;
    }

    // 台站

    @PostMapping("/stations")
    @ResponseStatus(HttpStatus.CREATED)
    public StationDto createStation(@Valid @RequestBody StationRequest req) {
        return DtoMapper.station(service.createStation(req));
    }

    @GetMapping("/stations")
    public List<StationDto> stations() {
        return service.listStations().stream().map(DtoMapper::station).toList();
    }

    @GetMapping("/stations/{stationCode}")
    public StationDto station(@PathVariable String stationCode) {
        return DtoMapper.station(service.getStation(stationCode));
    }

    // 频率资源

    @PostMapping("/resources")
    @ResponseStatus(HttpStatus.CREATED)
    public FrequencyResourceDto createResource(@Valid @RequestBody FrequencyResourceRequest req) {
        return DtoMapper.resource(service.createResource(req));
    }

    @GetMapping("/resources")
    public List<FrequencyResourceDto> resources() {
        return service.listResources().stream().map(DtoMapper::resource).toList();
    }

    // 规则版本

    @PostMapping("/rule-versions")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleVersionDto publishRule(@Valid @RequestBody RuleVersionRequest req) {
        return DtoMapper.rule(service.publishRuleVersion(req));
    }

    @GetMapping("/rule-versions")
    public List<RuleVersionDto> rules() {
        return service.listRuleVersions().stream().map(DtoMapper::rule).toList();
    }
}
