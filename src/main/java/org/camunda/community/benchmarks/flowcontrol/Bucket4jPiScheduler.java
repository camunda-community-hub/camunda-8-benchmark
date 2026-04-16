package org.camunda.community.benchmarks.flowcontrol;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.TokensInheritanceStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import org.camunda.community.benchmarks.StartPiExecutor;
import org.camunda.community.benchmarks.StatisticsCollector;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Bucket4j-based process instance scheduler using virtual threads.
 * <p>
 * Replaces the traditional 10ms batch scheduling loop with a simpler model:
 * virtual threads consume tokens from a Bucket4j bucket and fire PI starts.
 * The bucket's refill rate controls throughput, and the {@link FlowControlInterceptor}
 * provides backpressure feedback by penalizing the bucket on RESOURCE_EXHAUSTED.
 * <p>
 * Supports two strategies:
 * <ul>
 *   <li>{@code backoff} — fixed rate with penalty-based backoff, recovers to target</li>
 *   <li>{@code autoTune} — periodic rate adjustment to discover cluster capacity</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "benchmark.startProcesses", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression(
    "'${benchmark.startRateAdjustmentStrategy:backpressure}' == 'backoff' or "
  + "'${benchmark.startRateAdjustmentStrategy:backpressure}' == 'autoTune'")
public class Bucket4jPiScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(Bucket4jPiScheduler.class);
    private static final int VIRTUAL_THREAD_COUNT = 100;

    private final Bucket bucket;
    private final StartPiExecutor executor;
    private final StatisticsCollector stats;
    private final BenchmarkConfiguration config;
    private final FlowControlInterceptor interceptor;
    private final List<Thread> workers = new ArrayList<>();

    private volatile long currentRate;

    public Bucket4jPiScheduler(
            Bucket flowControlBucket,
            StartPiExecutor executor,
            StatisticsCollector stats,
            BenchmarkConfiguration config,
            FlowControlInterceptor flowControlInterceptor,
            MeterRegistry meterRegistry) {
        this.bucket = flowControlBucket;
        this.executor = executor;
        this.stats = stats;
        this.config = config;
        this.interceptor = flowControlInterceptor;
        this.currentRate = config.getStartPiPerSecond();

        meterRegistry.gauge("pi_rate_goal", this, s -> s.currentRate);
        meterRegistry.gauge("flowcontrol_available_tokens", bucket, Bucket::getAvailableTokens);
    }

    @PostConstruct
    public void start() {
        String strategy = config.getStartRateAdjustmentStrategy();
        LOG.info("Starting Bucket4j PI scheduler: strategy={}, initialRate={}/s, virtualThreads={}",
                strategy, currentRate, VIRTUAL_THREAD_COUNT);
        stats.hintOnNewPiPerSecondGoald(currentRate);

        for (int i = 0; i < VIRTUAL_THREAD_COUNT; i++) {
            Thread worker = Thread.ofVirtual()
                    .name("bucket4j-pi-starter-", i)
                    .start(this::startLoop);
            workers.add(worker);
        }
    }

    private void startLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                bucket.asBlocking().consume(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            executor.startInstance();
            stats.incStartedProcessInstances();
        }
    }

    /**
     * Periodic rate adjustment for the {@code autoTune} strategy.
     * <p>
     * Reads backpressure and total call counts from the interceptor, calculates
     * the backpressure percentage, and adjusts the bucket's refill rate using
     * the configured reduce/increase factors. Mirrors the logic of
     * {@code RateCalculator.adjustByUsingBackpressure()} but with Bucket4j.
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    public void adjustRate() {
        if (!"autoTune".equals(config.getStartRateAdjustmentStrategy())) {
            return;
        }

        long recentBackpressure = interceptor.getBackpressureCount().getAndSet(0);
        long recentTotal = interceptor.getTotalCallCount().getAndSet(0);

        if (recentTotal == 0) {
            return;
        }

        double backpressurePercent = (double) recentBackpressure / recentTotal * 100.0;

        long previousRate = currentRate;
        if (backpressurePercent > config.getMaxBackpressurePercentage()) {
            currentRate = Math.max(1, Math.round(currentRate * (1.0 - config.getStartPiReduceFactor())));
            LOG.info("AutoTune: backpressure {}% > {}% threshold, reducing rate {} -> {}/s",
                    String.format("%.1f", backpressurePercent),
                    String.format("%.1f", config.getMaxBackpressurePercentage()),
                    previousRate, currentRate);
        } else {
            currentRate = Math.max(1, Math.round(currentRate * (1.0 + config.getStartPiIncreaseFactor())));
            LOG.info("AutoTune: backpressure {}% <= {}% threshold, increasing rate {} -> {}/s",
                    String.format("%.1f", backpressurePercent),
                    String.format("%.1f", config.getMaxBackpressurePercentage()),
                    previousRate, currentRate);
        }

        Bandwidth newLimit = Bandwidth.builder()
                .capacity(currentRate)
                .refillGreedy(currentRate, Duration.ofSeconds(1))
                .build();
        bucket.replaceConfiguration(
                BucketConfiguration.builder().addLimit(newLimit).build(),
                TokensInheritanceStrategy.PROPORTIONALLY);
        stats.hintOnNewPiPerSecondGoald(currentRate);
    }

    @PreDestroy
    public void stop() {
        LOG.info("Stopping Bucket4j PI scheduler ({} virtual threads)", workers.size());
        workers.forEach(Thread::interrupt);
    }
}
