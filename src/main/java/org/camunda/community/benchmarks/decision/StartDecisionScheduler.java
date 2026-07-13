package org.camunda.community.benchmarks.decision;

import jakarta.annotation.PostConstruct;
import org.camunda.community.benchmarks.StatisticsCollector;
import org.camunda.community.benchmarks.common.BenchmarkScheduler;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.camunda.community.benchmarks.config.FlowControlStrategyExpressions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Gated the same way as {@code StartPiScheduler}: {@code RateCalculator.adjustStartRates()}
 * (inherited from {@link BenchmarkScheduler}) throws for any strategy other than
 * {@code none}/{@code backpressure}/{@code jobRatio}, and there is no Bucket4j-based
 * decision scheduler yet, so this component must stay off when {@code backoff}/{@code autoTune}
 * is selected — otherwise its {@code @Scheduled} rate adjustment fails every 30s.
 */
@Component
@ConditionalOnProperty(name = "benchmark.startDecisions", havingValue = "true", matchIfMissing = false)
@ConditionalOnExpression(FlowControlStrategyExpressions.IS_NOT_FLOW_CONTROL_STRATEGY)
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
