package com.chris64233.spectrumcoordination.config;

import com.chris64233.spectrumcoordination.service.SpectrumCoordinationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时将到达开始时间的未开始许可置为生效（占用在发证时已写入）。 */
@Component
public class LicenseActivationScheduler {

    private final SpectrumCoordinationService coordinationService;

    public LicenseActivationScheduler(SpectrumCoordinationService coordinationService) {
        this.coordinationService = coordinationService;
    }

    @Scheduled(fixedDelayString = "${spectrum.license.activation-delay-ms:60000}")
    public void activateDueLicenses() {
        coordinationService.activateDueLicenses();
    }
}
