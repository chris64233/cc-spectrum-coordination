package com.chris64233.spectrumcoordination.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.chris64233.spectrumcoordination.domain.ApplicationStatus;
import com.chris64233.spectrumcoordination.dto.ApplicationRequest;
import com.chris64233.spectrumcoordination.dto.DecisionResult;
import com.chris64233.spectrumcoordination.dto.FrequencyResourceRequest;
import com.chris64233.spectrumcoordination.dto.RuleVersionRequest;
import com.chris64233.spectrumcoordination.dto.StationRequest;
import com.chris64233.spectrumcoordination.repo.FrequencyApplicationRepository;
import com.chris64233.spectrumcoordination.repo.FrequencyOccupancyRepository;
import com.chris64233.spectrumcoordination.support.DatabaseCleaner;
import com.chris64233.spectrumcoordination.support.TestClockConfig;

/**
 * 并发场景测试：
 * <ul>
 *   <li>相邻区域同一频段的并发裁决，最终结果不得违反同频使用上限；</li>
 *   <li>相同申请号的并发提交必须幂等，只落一条申请。</li>
 * </ul>
 */
@SpringBootTest
@Import(TestClockConfig.class)
class SpectrumConcurrencyTest {

    @Autowired
    private SpectrumService service;
    @Autowired
    private FrequencyApplicationRepository applicationRepo;
    @Autowired
    private FrequencyOccupancyRepository occupancyRepo;
    @Autowired
    private DatabaseCleaner cleaner;

    private static final Instant T0 = TestClockConfig.BASE_TIME;
    private long resourceId;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        service.publishRuleVersion(new RuleVersionRequest(0.0, "v1"));
        resourceId = service.createResource(
                new FrequencyResourceRequest(100, 200, 0, 0, 1000, 1)).getId();
    }

    @Test
    void concurrentDecisionsForAdjacentSameBandNeverExceedCapacity() throws Exception {
        int threads = 8;
        // 8 个台站位置略有差异（0.0005° ~ 0.004°），但相互距离均小于半径之和（各 10km，和 20km）
        for (int i = 0; i < threads; i++) {
            String code = "STA-C" + i;
            service.createStation(new StationRequest(code, "台" + i, 0.0, 0.0, 10, 50, "X"));
            service.submitApplication(new ApplicationRequest(
                    "CONC-" + i, code, resourceId, 100, 110,
                    0.0, 0.0005 * i, 10,
                    T0.plus(1, ChronoUnit.HOURS), T0.plus(12, ChronoUnit.HOURS)));
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<DecisionResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.decide("CONC-" + idx);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int approved = 0;
            int rejected = 0;
            for (Future<DecisionResult> f : futures) {
                DecisionResult r = f.get(30, TimeUnit.SECONDS);
                if (r.approved()) {
                    approved++;
                } else if (r.status() == ApplicationStatus.REJECTED) {
                    rejected++;
                }
            }
            // maxCoChannelUsers = 1：恰好 1 个批准，其余全部拒绝
            assertThat(approved).isEqualTo(1);
            assertThat(rejected).isEqualTo(threads - 1);
            assertThat(occupancyRepo.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentSubmitWithSameApplicationNoIsIdempotent() throws Exception {
        service.createStation(new StationRequest("STA-IDEM", "幂等台", 0, 0, 10, 10, "X"));
        String applicationNo = "IDEMPOTENT-" + UUID.randomUUID();

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        ExecutorCompletionService<Boolean> completion = new ExecutorCompletionService<>(pool);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int i = 0; i < threads; i++) {
                Callable<Boolean> task = () -> {
                    ready.countDown();
                    start.await();
                    try {
                        service.submitApplication(new ApplicationRequest(
                                applicationNo, "STA-IDEM", resourceId, 100, 110,
                                0, 0, 10,
                                T0.plus(1, ChronoUnit.HOURS), T0.plus(12, ChronoUnit.HOURS)));
                        return true;
                    } catch (Exception ex) {
                        return false;
                    }
                };
                completion.submit(task);
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successes = 0;
            for (int i = 0; i < threads; i++) {
                if (completion.take().get(30, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            // 所有并发请求都得到成功响应（首个创建，其余幂等重读）
            assertThat(successes).isEqualTo(threads);
            assertThat(applicationRepo.existsByApplicationNo(applicationNo)).isTrue();
            assertThat(applicationRepo.findAll()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
