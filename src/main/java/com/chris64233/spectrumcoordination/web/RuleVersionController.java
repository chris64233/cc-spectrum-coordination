package com.chris64233.spectrumcoordination.web;

import com.chris64233.spectrumcoordination.domain.RuleVersion;
import com.chris64233.spectrumcoordination.service.RuleVersionService;
import com.chris64233.spectrumcoordination.web.dto.RuleVersionCreateRequest;
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

/** 规则版本查询、创建与激活。规则版本切换后，待批准申请在批准时按新版本重新校验。 */
@RestController
@RequestMapping("/api/rule-versions")
public class RuleVersionController {

    private final RuleVersionService ruleVersionService;

    public RuleVersionController(RuleVersionService ruleVersionService) {
        this.ruleVersionService = ruleVersionService;
    }

    @GetMapping
    public List<RuleVersion> list() {
        return ruleVersionService.all();
    }

    @GetMapping("/current")
    public RuleVersion current() {
        return ruleVersionService.current();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleVersion create(@Valid @RequestBody RuleVersionCreateRequest req) {
        return ruleVersionService.create(req.version(), req.marginMeters(), req.distanceFactor());
    }

    @PostMapping("/{id}/activate")
    public RuleVersion activate(@PathVariable Long id) {
        return ruleVersionService.activate(id);
    }
}
