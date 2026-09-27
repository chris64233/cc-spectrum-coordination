package com.chris64233.spectrumcoordination.web;

import com.chris64233.spectrumcoordination.domain.ApplicationConflict;
import com.chris64233.spectrumcoordination.domain.FrequencyApplication;
import com.chris64233.spectrumcoordination.service.SpectrumCoordinationService;
import com.chris64233.spectrumcoordination.web.dto.ApplicationSubmitRequest;
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

/** 频率许可申请：提交（幂等）、批准、查询、冲突对象查询。 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final SpectrumCoordinationService coordinationService;

    public ApplicationController(SpectrumCoordinationService coordinationService) {
        this.coordinationService = coordinationService;
    }

    /** 提交申请。相同申请号重复提交返回首次申请（幂等）。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FrequencyApplication submit(@Valid @RequestBody ApplicationSubmitRequest req) {
        return coordinationService.submit(req);
    }

    /** 按当前生效规则版本重新校验并批准；整段频段全有或全无。 */
    @PostMapping("/{applicationNo}/approve")
    public SpectrumCoordinationService.ApprovalResult approve(@PathVariable String applicationNo) {
        return coordinationService.approve(applicationNo);
    }

    @GetMapping("/{applicationNo}")
    public FrequencyApplication get(@PathVariable String applicationNo) {
        return coordinationService.getApplication(applicationNo);
    }

    /** 查询申请最近一次校验记录的冲突对象（含许可、台站与重叠量）。 */
    @GetMapping("/{applicationNo}/conflicts")
    public List<ApplicationConflict> conflicts(@PathVariable String applicationNo) {
        FrequencyApplication app = coordinationService.getApplication(applicationNo);
        return coordinationService.conflicts(app.getId());
    }
}
