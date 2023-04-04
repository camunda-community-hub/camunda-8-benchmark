package org.camunda.community.benchmarks;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.impl.ZeebeClientImpl;
import io.camunda.zeebe.spring.client.jobhandling.CommandWrapper;
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
public class StartDiExecutor {

    public static final String BENCHMARK_START_DATE_MILLIS = "benchmark_di_start_date_millis";
    private static final Object BENCHMARK_STARTER_ID = "benchmark_di_starter_id";

    @Autowired
    private BenchmarkConfiguration config;

    @Autowired
    private ZeebeClient client;

    @Autowired
    private BenchmarkStartPiExceptionHandlingStrategy exceptionHandlingStrategy;

    @Autowired
    private ZeebeClientConfiguration zeebeClientConfiguration;

    private Map<String, Object> benchmarkPayload;

    @PostConstruct
    public void init() throws IOException {
        String variablesJsonString = tryReadVariables(config.getPayloadPath().getInputStream());
        benchmarkPayload = zeebeClientConfiguration.getJsonMapper().fromJsonAsMap(variablesJsonString);
    }

    public void startDecisionInstance() {
        HashMap<Object, Object> variables = new HashMap<>();
        variables.putAll(this.benchmarkPayload);
        variables.put("listItem","c");
        variables.put(BENCHMARK_START_DATE_MILLIS, Instant.now().toEpochMilli());
        variables.put(BENCHMARK_STARTER_ID, config.getStarterId());

        // Auto-complete logic from https://github.com/camunda-community-hub/spring-zeebe/blob/ec41c5af1f64e512c8e7a8deea2aeacb35e61a16/client/spring-zeebe/src/main/java/io/camunda/zeebe/spring/client/jobhandling/JobHandlerInvokingSpringBeans.java#L24
        FinalCommandStep createCommand = client.newEvaluateDecisionCommand()
                .decisionId("benchmark-decision")
                .variables(variables);
        CommandWrapper command = new RefactoredCommandWrapper(
                createCommand,
                System.currentTimeMillis() + 5 * 60 * 1000, // 5 minutes
                "CreateDi" + config.getDmnDecisionId(),
                exceptionHandlingStrategy);
        command.executeAsync();
    }

    private String tryReadVariables(final InputStream inputStream) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        try (final InputStreamReader reader = new InputStreamReader(inputStream)) {
            try (final BufferedReader br = new BufferedReader(reader)) {
                String line;
                while ((line = br.readLine()) != null) {
                    stringBuilder.append(line).append("\n");
                }
            }
        }
        return stringBuilder.toString();
    }
}
