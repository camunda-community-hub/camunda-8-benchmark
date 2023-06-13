package org.camunda.community.benchmarks;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

@Component
public abstract class StartInstanceExecutor {

    @Autowired
    protected BenchmarkConfiguration config;

    @Autowired
    protected ZeebeClient client;

    @Autowired
    protected ZeebeClientConfiguration zeebeClientConfiguration;

    protected Map<String, Object> benchmarkPayload;

    @PostConstruct
    public void init() throws IOException {
        String variablesJsonString = tryReadVariables(config.getPayloadPath().getInputStream());
        benchmarkPayload = zeebeClientConfiguration.getJsonMapper().fromJsonAsMap(variablesJsonString);
    }

    public abstract void startInstance();

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
