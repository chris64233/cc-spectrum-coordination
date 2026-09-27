package com.chris64233.spectrumcoordination.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.spectrumcoordination.dto.ChangeRecordDto;
import com.chris64233.spectrumcoordination.dto.ConflictDto;
import com.chris64233.spectrumcoordination.dto.LicenseDto;
import com.chris64233.spectrumcoordination.dto.RetuneRequest;
import com.chris64233.spectrumcoordination.service.SpectrumService;

/**
 * 许可接口：查询、冲突对象、改频（仅未开始）、暂停/恢复/提前终止（已开始）与变更记录。
 */
@RestController
@RequestMapping("/api/licenses")
public class LicenseController {

    private final SpectrumService service;

    public LicenseController(SpectrumService service) {
        this.service = service;
    }

    @GetMapping
    public List<LicenseDto> list() {
        return service.listLicenses();
    }

    @GetMapping("/{licenseNo}")
    public LicenseDto get(@PathVariable String licenseNo) {
        return service.getLicense(licenseNo);
    }

    @GetMapping("/{licenseNo}/conflicts")
    public List<ConflictDto> conflicts(@PathVariable String licenseNo) {
        return service.licenseConflicts(licenseNo);
    }

    @PostMapping("/{licenseNo}/retune")
    public LicenseDto retune(@PathVariable String licenseNo, @Valid @RequestBody RetuneRequest req) {
        return service.retune(licenseNo, req);
    }

    @PostMapping("/{licenseNo}/suspend")
    public LicenseDto suspend(@PathVariable String licenseNo) {
        return service.suspend(licenseNo);
    }

    @PostMapping("/{licenseNo}/resume")
    public LicenseDto resume(@PathVariable String licenseNo) {
        return service.resume(licenseNo);
    }

    @PostMapping("/{licenseNo}/terminate")
    public LicenseDto terminate(@PathVariable String licenseNo) {
        return service.terminate(licenseNo);
    }

    @GetMapping("/{licenseNo}/changes")
    public List<ChangeRecordDto> changes(@PathVariable String licenseNo) {
        return service.changesOfLicense(licenseNo);
    }

    @GetMapping("/changes/recent")
    public List<ChangeRecordDto> recentChanges(@RequestParam(defaultValue = "50") int limit) {
        return service.latestChanges(limit);
    }
}
