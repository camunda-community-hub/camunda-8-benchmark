package org.camunda.community.benchmarks;

import com.google.common.util.concurrent.UncheckedExecutionException;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.spring.client.jobhandling.CommandWrapper;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.camunda.community.benchmarks.refactoring.RefactoredCommandWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;

@Component
public class StartPiExecutor {

    @Autowired
    private BenchmarkConfiguration config;

    @Autowired
    private ZeebeClient client;

    @Autowired
    private BenchmarkStartPiExceptionHandlingStrategy exceptionHandlingStrategy;

    // TODO: Check if we can/need to check if the scheduler can catch up with all its work (or if it is overwhelmed)
    @Autowired
    private TaskScheduler scheduler;

    private String variables;

    @PostConstruct
    public void init() {
        variables = readVariables(config.getPayloadPath());
    }

    public void startProcessInstance() {
        //System.out.print(".");
        // Auto-complete logic from https://github.com/camunda-community-hub/spring-zeebe/blob/ec41c5af1f64e512c8e7a8deea2aeacb35e61a16/client/spring-zeebe/src/main/java/io/camunda/zeebe/spring/client/jobhandling/JobHandlerInvokingSpringBeans.java#L24
        FinalCommandStep createCommand = client.newCreateInstanceCommand()
                .bpmnProcessId(config.getBpmnProcessId())
                .latestVersion()
                .variables(variables);
        CommandWrapper command = new RefactoredCommandWrapper(
                createCommand,
                System.currentTimeMillis() + 5 * 60 * 1000, // 5 minutes
                "CreatePi" + config.getBpmnProcessId(),
                exceptionHandlingStrategy);
        command.executeAsync();
    }

    protected String readVariables(final String payloadPath) {
        try {
            final var classLoader = StartPiExecutor.class.getClassLoader();
            try (final InputStream fromResource = classLoader.getResourceAsStream(payloadPath)) {
                if (fromResource != null) {
                    return tryReadVariables(fromResource);
                }
                // unable to find from resources, try as regular file
                try (final InputStream fromFile = new FileInputStream(payloadPath)) {
                    return tryReadVariables(fromFile);
                }
            }
        } catch (final IOException e) {
            throw new UncheckedExecutionException(e);
        }
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
