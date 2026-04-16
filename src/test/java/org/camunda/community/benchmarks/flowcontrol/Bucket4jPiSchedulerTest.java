package org.camunda.community.benchmarks.flowcontrol;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.camunda.community.benchmarks.StartPiExecutor;
import org.camunda.community.benchmarks.StatisticsCollector;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class Bucket4jPiSchedulerTest {

    @Mock
    private StartPiExecutor executor;

    @Mock
    private StatisticsCollector stats;

    @Mock
    private BenchmarkConfiguration config;

    private SimpleMeterRegistry meterRegistry;
    private Bucket bucket;
    private FlowControlInterceptor interceptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
    }

    private Bucket4jPiScheduler createScheduler(long startPiPerSecond, String strategy) {
        when(config.getStartPiPerSecond()).thenReturn(startPiPerSecond);
        when(config.getStartRateAdjustmentStrategy()).thenReturn(strategy);
        when(config.getMaxBackpressurePercentage()).thenReturn(10.0);
        when(config.getStartPiReduceFactor()).thenReturn(0.1);
        when(config.getStartPiIncreaseFactor()).thenReturn(0.4);

        bucket = Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(startPiPerSecond)
                        .refillGreedy(startPiPerSecond, Duration.ofSeconds(1))
                        .build())
                .build();

        long penaltyTokens = Math.max(1, Math.round(startPiPerSecond * 0.1));
        interceptor = new FlowControlInterceptor(bucket, penaltyTokens, stats);

        // Constructor does NOT call start(), so no virtual threads are spawned
        return new Bucket4jPiScheduler(bucket, executor, stats, config, interceptor, meterRegistry);
    }

    private double rateGoal() {
        return meterRegistry.get("pi_rate_goal").gauge().value();
    }

    @Test
    void adjustRate_highBackpressure_reducesRate() {
        Bucket4jPiScheduler scheduler = createScheduler(100, "autoTune");

        // Simulate 20% backpressure (above 10% threshold)
        interceptor.getBackpressureCount().set(20);
        interceptor.getTotalCallCount().set(100);

        scheduler.adjustRate();

        // 100 * (1 - 0.1) = 90
        assertEquals(90, (long) rateGoal());
    }

    @Test
    void adjustRate_lowBackpressure_increasesRate() {
        Bucket4jPiScheduler scheduler = createScheduler(100, "autoTune");

        // Simulate 5% backpressure (below 10% threshold)
        interceptor.getBackpressureCount().set(5);
        interceptor.getTotalCallCount().set(100);

        scheduler.adjustRate();

        // 100 * (1 + 0.4) = 140
        assertEquals(140, (long) rateGoal());
    }

    @Test
    void adjustRate_zeroTotalCalls_noAdjustment() {
        Bucket4jPiScheduler scheduler = createScheduler(100, "autoTune");

        // No calls at all
        interceptor.getBackpressureCount().set(0);
        interceptor.getTotalCallCount().set(0);

        scheduler.adjustRate();

        assertEquals(100, (long) rateGoal());
    }

    @Test
    void adjustRate_backoffStrategy_isNoOp() {
        Bucket4jPiScheduler scheduler = createScheduler(100, "backoff");

        interceptor.getBackpressureCount().set(50);
        interceptor.getTotalCallCount().set(100);

        scheduler.adjustRate();

        // Rate unchanged because strategy is backoff, not autoTune
        assertEquals(100, (long) rateGoal());
    }

    @Test
    void adjustRate_rateNeverDropsBelowOne() {
        Bucket4jPiScheduler scheduler = createScheduler(1, "autoTune");

        // 100% backpressure
        interceptor.getBackpressureCount().set(100);
        interceptor.getTotalCallCount().set(100);

        scheduler.adjustRate();

        // max(1, round(1 * 0.9)) = 1
        assertEquals(1, (long) rateGoal());
    }

    @Test
    void adjustRate_resetsInterceptorCounters() {
        Bucket4jPiScheduler scheduler = createScheduler(100, "autoTune");

        interceptor.getBackpressureCount().set(10);
        interceptor.getTotalCallCount().set(50);

        scheduler.adjustRate();

        assertEquals(0, interceptor.getBackpressureCount().get());
        assertEquals(0, interceptor.getTotalCallCount().get());
    }

    @Test
    void constructor_registersGauges() {
        createScheduler(500, "autoTune");

        assertNotNull(meterRegistry.find("pi_rate_goal").gauge());
        assertNotNull(meterRegistry.find("flowcontrol_available_tokens").gauge());
        assertEquals(500, (long) rateGoal());
    }
}
