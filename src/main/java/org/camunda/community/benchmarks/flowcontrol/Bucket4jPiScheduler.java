package org.camunda.community.benchmarks.flowcontrol;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.TokensInheritanceStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import org.camunda.community.benchmarks.StartPiExecutor;
import org.camunda.community.benchmarks.StatisticsCollector;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.camunda.community.benchmarks.config.FlowControlStrategyExpressions;
import org.camunda.community.benchmarks.utils.BpmnJobTypeParser;
import org.camunda.community.benchmarks.utils.JobTypeCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Bucket4j-based process instance scheduler using a virtual thread.
 * <p>
 * Replaces the traditional 10ms batch scheduling loop with a simpler model: a virtual thread
 * consumes tokens from a Bucket4j bucket and fires PI starts. The bucket's refill rate controls
 * throughput, and the {@link FlowControlInterceptor} provides backpressure feedback by
 * penalizing the bucket on RESOURCE_EXHAUSTED.
 * <p>
 * A single virtual thread is enough here: {@link StartPiExecutor#startInstance()} is fire-and-forget
 * (it dispatches the gRPC call asynchronously and returns immediately), so there is no blocking
 * work per iteration to parallelize — pacing is governed entirely by the token bucket, not by
 * how many threads are polling it. A fixed pool of worker threads would just be the classic
 * thread-pool pattern grafted onto virtual threads, which doesn't need it.
 * <p>
 * Supports three strategies:
 * <ul>
 *   <li>{@code backoff} — fixed rate with penalty-based backoff, recovers to target</li>
 *   <li>{@code autoTune} — gRPC-backpressure-driven rate discovery, see {@link #adjustRate()}</li>
 *   <li>{@code autoTuneJobRatio} — job-completion-rate-driven rate discovery, see
 *       {@link #adjustRateByJobRatio()}; a better signal where applicable, but requires this same
 *       instance to also run job workers for the deployed process</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "benchmark.startProcesses", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression(FlowControlStrategyExpressions.IS_FLOW_CONTROL_STRATEGY)
public class Bucket4jPiScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(Bucket4jPiScheduler.class);

    private final Bucket bucket;
    private final StartPiExecutor executor;
    private final StatisticsCollector stats;
    private final BenchmarkConfiguration config;
    private final RateGoal rate;

    /**
     * Distinct job types for the deployed process (BPMN-discovered union configured), i.e. how
     * many job completions one fully-executed process instance is expected to produce. Only used
     * by {@code autoTuneJobRatio}; computed unconditionally at startup regardless of strategy
     * since it's cheap and doesn't depend on {@code benchmark.startWorkers} being enabled here.
     */
    private final int jobsPerInstance;

    private volatile Thread worker;

    /**
     * Once {@code true}, {@link #adjustRate()}/{@link #adjustRateByJobRatio()} has seen a real
     * congestion event and permanently stays in additive-increase mode instead of ever returning
     * to exponential slow-start growth. This assumes a roughly fixed-size target cluster for the
     * lifetime of a single benchmark run.
     */
    private volatile boolean slowStart = true;

    public Bucket4jPiScheduler(
            Bucket flowControlBucket,
            StartPiExecutor executor,
            StatisticsCollector stats,
            BenchmarkConfiguration config,
            RateGoal rateGoal,
            MeterRegistry meterRegistry) {
        this.bucket = flowControlBucket;
        this.executor = executor;
        this.stats = stats;
        this.config = config;
        this.rate = rateGoal;
        this.jobsPerInstance = countJobTypes(config);

        meterRegistry.gauge("pi_rate_goal", rate, RateGoal::get);
        meterRegistry.gauge("flowcontrol_available_tokens", bucket, Bucket::getAvailableTokens);
    }

    private static int countJobTypes(BenchmarkConfiguration config) {
        Set<String> jobTypes = new HashSet<>(JobTypeCounter.fromConfiguration(config));
        if (config.isAutoDeployProcess() && config.getBpmnResource() != null && config.getBpmnResource().length > 0) {
            jobTypes.addAll(BpmnJobTypeParser.extractJobTypes(config.getBpmnResource()));
        }
        return jobTypes.size();
    }

    @PostConstruct
    public void start() {
        String strategy = config.getStartRateAdjustmentStrategy();
        LOG.info("Starting Bucket4j PI scheduler: strategy={}, initialRate={}/s, jobsPerInstance={}",
                strategy, rate.get(), jobsPerInstance);
        stats.hintOnNewPiPerSecondGoald(rate.get());

        if ("autoTuneJobRatio".equals(strategy) && jobsPerInstance <= 0) {
            LOG.warn("autoTuneJobRatio selected, but no job types were found for the deployed process "
                    + "(BPMN parsing disabled/no service tasks, or benchmark.autoDeployProcess=false with "
                    + "no configured benchmark.jobType). This strategy cannot compute an expected job "
                    + "completion rate without at least one job type and will never adjust the rate — "
                    + "use autoTune or backoff instead for processes without jobs.");
        }

        worker = Thread.ofVirtual().name("bucket4j-pi-starter").start(this::startLoop);
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
     * Periodic rate adjustment for the {@code autoTune} strategy: TCP-style slow-start followed
     * by AIMD (additive-increase/multiplicative-decrease) congestion avoidance.
     * <p>
     * Reads the PI-start and PI-start-backpressure one-minute rates from {@link StatisticsCollector}
     * (the same rolling meters {@code common.RateCalculator}'s classic {@code backpressure} strategy
     * uses) rather than the interceptor's own raw counters — those meters decay smoothly instead of
     * being hard-reset every tick, so a rejection that resolves a little late still counts toward
     * the window it was actually sent in instead of silently vanishing into whichever tick happens
     * to be open when it finally resolves.
     * <p>
     * Growth is exponential ({@code previousRate * (1 + startPiIncreaseFactor)}) only until the
     * first tick where backpressure exceeds {@code maxBackpressurePercentage}. From then on this
     * assumes the cluster's ceiling is roughly fixed for the rest of the run: it cuts hard
     * (multiplicative decrease, same as before) and permanently switches to small, fixed additive
     * steps (sized as {@code startPiPerSecond * startPiIncreaseFactor}, i.e. a fraction of the
     * originally configured rate, not of the current one) for any further increases. This bounds
     * how far a single misleading "healthy" tick can push the rate — the old always-multiplicative
     * growth had no such bound and could compound into runaway overshoot (and eventual OOM from the
     * resulting backlog of in-flight commands) long before a slow-to-resolve rejection ever caught
     * up in the accounting.
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    public void adjustRate() {
        if (!"autoTune".equals(config.getStartRateAdjustmentStrategy())) {
            return;
        }

        double startedRate = stats.getStartedPiMeter().getOneMinuteRate();
        if (startedRate <= 0) {
            return;
        }
        double backpressureRate = stats.getBackpressureOnStartPiMeter().getOneMinuteRate();
        double backpressurePercent = backpressureRate / startedRate * 100.0;
        boolean congested = backpressurePercent > config.getMaxBackpressurePercentage();

        String metric = String.format("backpressure %.1f%% (threshold %.1f%%)",
                backpressurePercent, config.getMaxBackpressurePercentage());
        applyNewRate(computeNextRate(congested, rate.get(), "AutoTune", metric));
    }

    /**
     * Periodic rate adjustment for the {@code autoTuneJobRatio} strategy: the same slow-start-then-
     * AIMD search as {@link #adjustRate()}, but driven by whether job completions are keeping pace
     * with PI starts instead of gRPC backpressure on the PI-start command.
     * <p>
     * Zeebe can accept/persist {@code CreateProcessInstance} commands (cheap: a log append) far
     * faster than it can activate/execute/export the resulting jobs (expensive), so PI-start
     * backpressure alone can badly lag real engine saturation — {@link #adjustRate()} may see a
     * "healthy" tick long after the engine is already falling behind. Job completions are a more
     * direct measurement of whether full process execution is keeping up: for a process with
     * {@code jobsPerInstance} tasks, a healthy pipeline completes roughly that many jobs per PI
     * started; if the observed completion rate falls below {@code minJobCompletionRatio} of that
     * expectation, that's this strategy's "congested" signal.
     * <p>
     * This only works when this same instance also runs job workers for the deployed process (the
     * common single-replica benchmark setup) — a "starter-only" replica
     * ({@code benchmark.startWorkers=false} in a "sticky" multi-starter deployment) has no local
     * job completions to measure and should use {@code autoTune}/{@code backoff} instead. Likewise
     * a process with no job types at all ({@code jobsPerInstance == 0}) has nothing to measure —
     * see the startup warning in {@link #start()}.
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    public void adjustRateByJobRatio() {
        if (!"autoTuneJobRatio".equals(config.getStartRateAdjustmentStrategy())) {
            return;
        }
        if (jobsPerInstance <= 0) {
            return; // already warned about this in start()
        }

        double startedRate = stats.getStartedPiMeter().getOneMinuteRate();
        if (startedRate <= 0) {
            return;
        }
        double completedJobsRate = stats.getCompletedJobsMeter().getOneMinuteRate();
        double expectedJobsRate = startedRate * jobsPerInstance;
        double completionRatio = completedJobsRate / expectedJobsRate;
        boolean congested = completionRatio < config.getMinJobCompletionRatio();

        String metric = String.format("job completion ratio %.2f (threshold %.2f, jobsPerInstance=%d)",
                completionRatio, config.getMinJobCompletionRatio(), jobsPerInstance);
        applyNewRate(computeNextRate(congested, rate.get(), "AutoTuneJobRatio", metric));
    }

    /**
     * Shared slow-start-then-AIMD decision: grow exponentially until the first {@code congested}
     * signal, then permanently switch to a hard multiplicative cut followed by small additive
     * steps for the rest of the run. See {@link #adjustRate()} for the full rationale.
     */
    private long computeNextRate(boolean congested, long previousRate, String strategyLabel, String metricDescription) {
        long newRate;
        if (congested) {
            // Once we've seen real congestion, stop trusting exponential growth for the rest of
            // this run and switch to careful additive steps instead of risking another unbounded
            // run-up the next time a tick happens to look healthy.
            slowStart = false;
            newRate = Math.max(1, Math.round(previousRate * (1.0 - config.getStartPiReduceFactor())));
            LOG.info("{}: {}, congested -> AIMD cut rate {} -> {}/s",
                    strategyLabel, metricDescription, previousRate, newRate);
        } else if (slowStart) {
            newRate = Math.max(1, Math.round(previousRate * (1.0 + config.getStartPiIncreaseFactor())));
            LOG.info("{}: {}, healthy -> slow-start increasing rate {} -> {}/s",
                    strategyLabel, metricDescription, previousRate, newRate);
        } else {
            long step = Math.max(1, Math.round(config.getStartPiPerSecond() * config.getStartPiIncreaseFactor()));
            newRate = previousRate + step;
            LOG.info("{}: {}, healthy -> AIMD additive increase rate {} -> {}/s (+{}/s)",
                    strategyLabel, metricDescription, previousRate, newRate, step);
        }
        return newRate;
    }

    private void applyNewRate(long newRate) {
        rate.set(newRate);

        Bandwidth newLimit = Bandwidth.builder()
                .capacity(newRate)
                .refillGreedy(newRate, Duration.ofSeconds(1))
                .build();
        bucket.replaceConfiguration(
                BucketConfiguration.builder().addLimit(newLimit).build(),
                TokensInheritanceStrategy.PROPORTIONALLY);
        stats.hintOnNewPiPerSecondGoald(newRate);
    }

    @PreDestroy
    public void stop() {
        LOG.info("Stopping Bucket4j PI scheduler");
        Thread w = worker;
        if (w == null) {
            return;
        }
        w.interrupt();
        try {
            w.join(Duration.ofSeconds(5).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
