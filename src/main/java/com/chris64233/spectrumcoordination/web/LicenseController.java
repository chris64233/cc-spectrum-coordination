package com.chris64233.spectrumcoordination.web;

import com.chris64233.spectrumcoordination.domain.License;
import com.chris64233.spectrumcoordination.domain.LicenseChangeRecord;
import com.chris64233.spectrumcoordination.domain.LicenseStatus;
import com.chris64233.spectrumcoordination.service.SpectrumCoordinationService;
import com.chris64233.spectrumcoordination.web.dto.ReassignRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 许可查询、未开始许可整体改频、已开始许可暂停/提前终止、变更记录查询。 */
@RestController
@RequestMapping("/api/licenses")
public class LicenseController {

    private final SpectrumCoordinationService coordinationService;

    public LicenseController(SpectrumCoordinationService coordinationService) {
        this.coordinationService = coordinationService;
    }

    @GetMapping
    public List<License> list(@RequestParam(required = false) Long stationId,
                              @RequestParam(required = false) LicenseStatus status) {
        return coordinationService.licenses(stationId, status);
    }

    @GetMapping("/{licenseNo}")
    public License get(@PathVariable String licenseNo) {
        return coordinationService.requireLicense(licenseNo);
    }

    /** 未开始许可整体改频：新频完整批准后才释放旧频；失败原许可保持有效。 */
    @PostMapping("/{licenseNo}/reassign")
    public SpectrumCoordinationService.ReassignResult reassign(
            @PathVariable String licenseNo, @Valid @RequestBody ReassignRequest req) {
        return coordinationService.reassign(licenseNo, req);
    }

    /** 已开始许可暂停（占用释放，许可保留）。 */
    @PostMapping("/{licenseNo}/pause")
    public License pause(@PathVariable String licenseNo) {
        return coordinationService.pause(licenseNo);
    }

    /** 提前终止（占用释放，许可历史保留）。 */
    @PostMapping("/{licenseNo}/terminate")
    public License terminate(@PathVariable String licenseNo) {
        return coordinationService.terminate(licenseNo);
    }

    /** 查询许可完整变更历史。 */
    @GetMapping("/{licenseNo}/changes")
    public List<LicenseChangeRecord> changes(@PathVariable String licenseNo) {
        License license = coordinationService.requireLicense(licenseNo);
        return coordinationService.changes(license.getId());
    }
}
