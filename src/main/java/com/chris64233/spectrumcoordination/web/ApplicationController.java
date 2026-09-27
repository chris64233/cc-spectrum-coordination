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

import com.chris64233.spectrumcoordination.dto.ApplicationDto;
import com.chris64233.spectrumcoordination.dto.ApplicationRequest;
import com.chris64233.spectrumcoordination.dto.ConflictDto;
import com.chris64233.spectrumcoordination.dto.DecisionResult;
import com.chris64233.spectrumcoordination.service.SpectrumService;

/**
 * 频率申请接口：提交（申请号幂等）、裁决（原子批准/拒绝）、查询与冲突预演。
 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final SpectrumService service;

    public ApplicationController(SpectrumService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationDto submit(@Valid @RequestBody ApplicationRequest req) {
        return service.submitApplication(req);
    }

    @GetMapping
    public List<ApplicationDto> list() {
        return service.listApplications();
    }

    @GetMapping("/{applicationNo}")
    public ApplicationDto get(@PathVariable String applicationNo) {
        return service.getApplication(applicationNo);
    }

    @PostMapping("/{applicationNo}/decide")
    public DecisionResult decide(@PathVariable String applicationNo) {
        return service.decide(applicationNo);
    }

    @PostMapping("/{applicationNo}/cancel")
    public ApplicationDto cancel(@PathVariable String applicationNo) {
        return service.cancelApplication(applicationNo);
    }

    @GetMapping("/{applicationNo}/conflicts")
    public List<ConflictDto> conflicts(@PathVariable String applicationNo) {
        return service.previewConflicts(applicationNo);
    }

    @GetMapping("/{applicationNo}/changes")
    public List<com.chris64233.spectrumcoordination.dto.ChangeRecordDto> changes(
            @PathVariable String applicationNo) {
        return service.changesOfApplication(applicationNo);
    }
}
