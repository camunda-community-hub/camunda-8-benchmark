package org.camunda.community.benchmarks.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.camunda.community.benchmarks.StatisticsCollector;
import org.camunda.community.benchmarks.flowcontrol.FlowControlInterceptor;
import org.camunda.community.benchmarks.flowcontrol.RateGoal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for Bucket4j-based flow control strategies ({@code backoff} and {@code autoTune}).
 * <p>
 * Creates the token bucket and gRPC interceptor that provide rate limiting and
 * backpressure feedback. The bucket capacity and refill rate are derived from
 * {@code startPiPerSecond}. The penalty is derived from {@code startPiReduceFactor}.
 */
@Configuration
@ConditionalOnExpression(FlowControlStrategyExpressions.IS_FLOW_CONTROL_STRATEGY)
public class FlowControlConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(FlowControlConfiguration.class);

    @Bean
    public RateGoal rateGoal(BenchmarkConfiguration config) {
        return new RateGoal(config.getStartPiPerSecond());
    }

    @Bean
    public Bucket flowControlBucket(BenchmarkConfiguration config) {
        long capacity = config.getStartPiPerSecond();
        long refillRate = config.getStartPiPerSecond();

        LOG.info("Creating flow control bucket: capacity={}, refillRate={}/s",
                capacity, refillRate);

        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillRate, Duration.ofSeconds(1))
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Bean
    public FlowControlInterceptor flowControlInterceptor(
            Bucket flowControlBucket, BenchmarkConfiguration config, StatisticsCollector stats,
            RateGoal rateGoal) {
        LOG.info("Creating flow control interceptor: reduceFactor={}", config.getStartPiReduceFactor());

        return new FlowControlInterceptor(flowControlBucket, rateGoal, config.getStartPiReduceFactor(), stats);
    }
}
