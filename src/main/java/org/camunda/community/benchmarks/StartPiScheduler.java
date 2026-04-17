package org.camunda.community.benchmarks;

import org.camunda.community.benchmarks.common.BenchmarkScheduler;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@ConditionalOnProperty(name = "benchmark.startProcesses", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression(
    "'${benchmark.startRateAdjustmentStrategy:backpressure}' != 'backoff' and "
  + "'${benchmark.startRateAdjustmentStrategy:backpressure}' != 'autoTune'")
public class StartPiScheduler extends BenchmarkScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(StartPiScheduler.class);

    @Autowired
    private BenchmarkConfiguration config;

    @Autowired
    private StatisticsCollector stats;

    @Autowired
    private StartPiExecutor executor;

    @PostConstruct
    public void init() {
        calculateParameters(config.getStartPiPerSecond());
    }

    @Async
    @Override
    protected void startInstances(long batchSize) {
        for (int i = 0; i < batchSize; i++) {
            executor.startInstance();
            stats.incStartedProcessInstances();
        }
    }
}
