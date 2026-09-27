package com.chris64233.spectrumcoordination.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.spectrumcoordination.domain.FrequencyApplication;
import com.chris64233.spectrumcoordination.domain.FrequencyResource;
import com.chris64233.spectrumcoordination.domain.InterferenceRuleVersion;
import com.chris64233.spectrumcoordination.domain.Station;
import com.chris64233.spectrumcoordination.dto.ApplicationRequest;
import com.chris64233.spectrumcoordination.repo.FrequencyApplicationRepository;

/**
 * 申请落库命令：独立事务执行，配合申请号唯一索引保证并发重复提交时的幂等。
 */
@Service
public class ApplicationCommandService {

    private final FrequencyApplicationRepository applications;
    private final Clock clock;

    public ApplicationCommandService(FrequencyApplicationRepository applications, Clock clock) {
        this.applications = applications;
        this.clock = clock;
    }

    /**
     * 在新事务中创建申请并立即刷盘。若申请号唯一约束被并发事务抢先占用，
     * 抛出 {@link DataIntegrityViolationException}，由上层回退为重读既有申请。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FrequencyApplication createNew(ApplicationRequest req, Station station,
                                          FrequencyResource resource, InterferenceRuleVersion rule) {
        if (applications.existsByApplicationNo(req.applicationNo())) {
            return null;
        }
        Instant now = clock.instant();
        FrequencyApplication a = new FrequencyApplication(req.applicationNo(), station, resource,
                req.bandStartMhz(), req.bandEndMhz(),
                req.useLatitude(), req.useLongitude(), req.useRadiusKm(),
                req.startTime(), req.endTime(), rule, now);
        applications.saveAndFlush(a);
        return a;
    }
}
