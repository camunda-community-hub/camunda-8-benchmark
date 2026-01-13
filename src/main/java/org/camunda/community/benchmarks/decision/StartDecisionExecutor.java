package org.camunda.community.benchmarks.decision;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.client.jobhandling.CommandWrapper;
import io.camunda.client.metrics.MicrometerMetricsRecorder;
import jakarta.annotation.PostConstruct;
import org.camunda.community.benchmarks.common.BenchmarkExecutor;
import org.camunda.community.benchmarks.StatisticsCollector;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.camunda.community.benchmarks.refactoring.RefactoredCommandWrapper;
import org.camunda.community.benchmarks.strategy.BenchmarkStartDecisionExceptionHandlingStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class StartDecisionExecutor extends BenchmarkExecutor {

  @Autowired
  private BenchmarkConfiguration config;

  @Autowired
  private CamundaClient client;

  @Autowired
  private BenchmarkStartDecisionExceptionHandlingStrategy exceptionHandlingStrategy;

  @Autowired
  private MicrometerMetricsRecorder micrometerMetricsRecorder;

  @Autowired
  private StatisticsCollector stats;

  @Autowired
  private CamundaClientConfiguration camundaClientConfiguration;

  private Map<String, Object> benchmarkPayload;

  @PostConstruct
  public void init() throws IOException {
    String variablesJsonString = tryReadVariables(config.getPayloadPath().getInputStream());
    benchmarkPayload = camundaClientConfiguration.getJsonMapper().fromJsonAsMap(variablesJsonString);
  }

  @Override
  public void startInstance() {
    Map<Object, Object> variables = new HashMap<>(benchmarkPayload);

    FinalCommandStep<EvaluateDecisionResponse> createCommand = client.newEvaluateDecisionCommand()
            .decisionId(config.getDmnDecisionId())
            .variables(variables);
    CommandWrapper command = new RefactoredCommandWrapper(
            createCommand,
            System.currentTimeMillis() + 5 * 60 * 1000, // 5 minutes
            "CreateDi" + config.getDmnDecisionId(),
            exceptionHandlingStrategy, micrometerMetricsRecorder);

    command.executeAsyncWithMetrics("DI_action","start",config.getDmnDecisionId());
    stats.incEvaluatedDecisionInstances();
  }
}
