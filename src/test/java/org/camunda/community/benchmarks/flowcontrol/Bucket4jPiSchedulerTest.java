package org.camunda.community.benchmarks.flowcontrol;

import com.codahale.metrics.Meter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.camunda.community.benchmarks.StartPiExecutor;
import org.camunda.community.benchmarks.StatisticsCollector;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class Bucket4jPiSchedulerTest {

    @Mock
    private StartPiExecutor executor;

    @Mock
    private StatisticsCollector stats;

    @Mock
    private BenchmarkConfiguration config;

    @Mock
    private Meter startedMeter;

    @Mock
    private Meter backpressureMeter;

    private SimpleMeterRegistry meterRegistry;
    private Bucket bucket;
    private Bucket4jPiScheduler scheduler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    private Bucket4jPiScheduler createScheduler(long startPiPerSecond, String strategy) {
        when(config.getStartPiPerSecond()).thenReturn(startPiPerSecond);
        when(config.getStartRateAdjustmentStrategy()).thenReturn(strategy);
        when(config.getMaxBackpressurePercentage()).thenReturn(10.0);
        when(config.getStartPiReduceFactor()).thenReturn(0.1);
        when(config.getStartPiIncreaseFactor()).thenReturn(0.4);
        when(stats.getStartedPiMeter()).thenReturn(startedMeter);
        when(stats.getBackpressureOnStartPiMeter()).thenReturn(backpressureMeter);

        bucket = Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(startPiPerSecond)
                        .refillGreedy(startPiPerSecond, Duration.ofSeconds(1))
                        .build())
                .build();

        RateGoal rate = new RateGoal(startPiPerSecond);

        // start() is not called here, so no worker thread is spawned
        scheduler = new Bucket4jPiScheduler(bucket, executor, stats, config, rate, meterRegistry);
        return scheduler;
    }

    /** Sets the one-minute rates {@code adjustRate()} reads, as a percentage of started calls. */
    private void simulateBackpressurePercent(double startedRatePerSecond, double backpressurePercent) {
        when(startedMeter.getOneMinuteRate()).thenReturn(startedRatePerSecond);
        when(backpressureMeter.getOneMinuteRate()).thenReturn(startedRatePerSecond * backpressurePercent / 100.0);
    }

    private double rateGoal() {
        return meterRegistry.get("pi_rate_goal").gauge().value();
    }

    @Test
    void adjustRate_highBackpressure_cutsRateMultiplicatively() {
        Bucket4jPiScheduler scheduler = createScheduler(100, "autoTune");

        simulateBackpressurePercent(100, 20); // 20% > 10% threshold
        scheduler.adjustRate();

        // 100 * (1 - 0.1) = 90
        assertEquals(90, (long) rateGoal());
    }

    @Test
    void adjustRate_lowBackpressureDuringSlowStart_increasesMultiplicatively() {
        Bucket4jPiScheduler scheduler = createScheduler(100, "autoTune");

        simulateBackpressurePercent(100, 5); // 5% <= 10% threshold, still in slow start
        scheduler.adjustRate();

        // 100 * (1 + 0.4) = 140
        assertEquals(140, (long) rateGoal());
    }

    @Test
    void adjustRate_lowBackpressureAfterCongestion_increasesAdditivelyNotMultiplicatively() {
        Bucket4jPiScheduler scheduler = createScheduler(100, "autoTune");

        // First tick: congestion, ends slow start. 100 * (1 - 0.1) = 90
        simulateBackpressurePercent(100, 20);
        scheduler.adjustRate();
        assertEquals(90, (long) rateGoal());

        // Second tick: healthy again, but must now increase additively, not multiplicatively.
        // step = round(startPiPerSecond * increaseFactor) = round(100 * 0.4) = 40 -> 90 + 40 = 130
        // (a multiplicative increase would instead give round(90 * 1.4) = 126)
        simulateBackpressurePercent(90, 5);
        scheduler.adjustRate();
        assertEquals(130, (long) rateGoal());
    }

    @Test
    void adjustRate_onceInAimdMode_neverReturnsToSlowStart() {
        Bucket4jPiScheduler scheduler = createScheduler(100, "autoTune");

        // Trigger the one-time transition out of slow start.
        simulateBackpressurePercent(100, 20);
        scheduler.adjustRate();
        assertEquals(90, (long) rateGoal());

        // Several subsequent healthy ticks in a row must all stay additive (+40/s each), never
        // reverting to the multiplicative slow-start growth even though backpressure looks fine
        // every time.
        simulateBackpressurePercent(90, 0);
        scheduler.adjustRate();
        assertEquals(130, (long) rateGoal()); // 90 + 40

        simulateBackpressurePercent(130, 0);
        scheduler.adjustRate();
        assertEquals(170, (long) rateGoal()); // 130 + 40, not 130 * 1.4 = 182
    }

    @Test
    void adjustRate_zeroStartedRate_noAdjustment() {
        Bucket4jPiScheduler scheduler = createScheduler(100, "autoTune");

        simulateBackpressurePercent(0, 0);
        scheduler.adjustRate();

        assertEquals(100, (long) rateGoal());
    }

    @Test
    void adjustRate_backoffStrategy_isNoOp() {
        Bucket4jPiScheduler scheduler = createScheduler(100, "backoff");

        simulateBackpressurePercent(100, 50);
        scheduler.adjustRate();

        // Rate unchanged because strategy is backoff, not autoTune
        assertEquals(100, (long) rateGoal());
    }

    @Test
    void adjustRate_rateNeverDropsBelowOne() {
        Bucket4jPiScheduler scheduler = createScheduler(1, "autoTune");

        simulateBackpressurePercent(1, 100); // 100% backpressure
        scheduler.adjustRate();

        // max(1, round(1 * 0.9)) = 1
        assertEquals(1, (long) rateGoal());
    }

    @Test
    void constructor_registersGauges() {
        createScheduler(500, "autoTune");

        assertNotNull(meterRegistry.find("pi_rate_goal").gauge());
        assertNotNull(meterRegistry.find("flowcontrol_available_tokens").gauge());
        assertEquals(500, (long) rateGoal());
    }

    /**
     * Exercises the actual worker thread ({@code start()}/{@code startLoop()}/{@code stop()}),
     * which the other tests above deliberately avoid (they never call {@code start()}, so no
     * worker thread is spawned). Uses a counting fake instead of asserting an exact count, since
     * the loop's throughput is timing-sensitive; the bounds are wide enough to tolerate slow CI
     * while still catching gross bugs (loop never running, or running far faster/slower than the
     * configured rate).
     */
    @Test
    void startLoop_observedCallRateTracksConfiguredRate() throws InterruptedException {
        long ratePerSecond = 200;
        AtomicInteger callCount = new AtomicInteger();
        doAnswer(invocation -> {
            callCount.incrementAndGet();
            return null;
        }).when(executor).startInstance();

        Bucket4jPiScheduler scheduler = createScheduler(ratePerSecond, "backoff");
        scheduler.start();

        long runMillis = 500;
        Thread.sleep(runMillis);
        scheduler.stop();

        int observed = callCount.get();
        long expected = ratePerSecond * runMillis / 1000;
        // Wide tolerance (25%-300% of expected) to absorb scheduling jitter on shared/slow CI
        // runners while still failing if the loop doesn't run at all or runs wildly off-rate.
        assertTrue(observed > expected * 0.25,
                "expected at least ~25% of " + expected + " calls in " + runMillis + "ms, got " + observed);
        assertTrue(observed < expected * 3,
                "expected at most ~300% of " + expected + " calls in " + runMillis + "ms, got " + observed);
    }
}
