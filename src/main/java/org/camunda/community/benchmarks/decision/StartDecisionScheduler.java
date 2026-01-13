package org.camunda.community.benchmarks.decision;

import jakarta.annotation.PostConstruct;
import org.camunda.community.benchmarks.StatisticsCollector;
import org.camunda.community.benchmarks.common.BenchmarkScheduler;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "benchmark.startDecisions", havingValue = "true", matchIfMissing = false)
public class StartDecisionScheduler extends BenchmarkScheduler {

  @Autowired
  private BenchmarkConfiguration config;

  @Autowired
  private StatisticsCollector stats;

  @Autowired
  private StartDecisionExecutor executor;

  @PostConstruct
  public void init() {
    calculateParameters(config.getStartDiPerSecond());
  }

  @Async
  @Override
  protected void startInstances(long batchSize) {
    for (int i = 0; i < batchSize; i++) {
      executor.startInstance();
      stats.incStartedDecisionInstances();
    }
  }
}
