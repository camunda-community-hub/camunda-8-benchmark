package org.camunda.community.benchmarks;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.impl.ZeebeClientImpl;
import io.camunda.zeebe.spring.client.jobhandling.CommandWrapper;
import io.camunda.zeebe.spring.client.jobhandling.DefaultCommandExceptionHandlingStrategy;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.camunda.community.benchmarks.refactoring.RefactoredCommandWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class StartDiExecutor extends StartInstanceExecutor {

    @Autowired
    private BenchmarkStartDiExceptionHandlingStrategy exceptionHandlingStrategy;

    @Autowired
    private StatisticsCollector stats;

    @Override
    public void startInstance() {
        HashMap<Object, Object> variables = new HashMap<>();
        variables.putAll(benchmarkPayload);

        FinalCommandStep createCommand = client.newEvaluateDecisionCommand()
                .decisionId(config.getDmnDecisionId())
                .variables(variables);
        CommandWrapper command = new RefactoredCommandWrapper(
                createCommand,
                System.currentTimeMillis() + 5 * 60 * 1000, // 5 minutes
                "CreateDi" + config.getDmnDecisionId(),
                exceptionHandlingStrategy);
         command.executeAsync();
         stats.incEvaluatedDecisionInstances();

    }

}
