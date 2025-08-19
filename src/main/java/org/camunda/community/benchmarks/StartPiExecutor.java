package org.camunda.community.benchmarks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.camunda.zeebe.spring.client.actuator.MicrometerMetricsRecorder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.camunda.community.benchmarks.partition.PartitionHashUtil;
import org.camunda.community.benchmarks.refactoring.RefactoredCommandWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.spring.client.jobhandling.CommandWrapper;
import jakarta.annotation.PostConstruct;

@Component
public class StartPiExecutor {

    private static final Logger LOG = LogManager.getLogger(StartPiExecutor.class);
    
    public static final String BENCHMARK_START_DATE_MILLIS = "benchmark_start_date_millis";
    private static final Object BENCHMARK_STARTER_ID = "benchmark_starter_id";
    
    // Message name for partition pinning
    private static final String PARTITION_PINNING_MESSAGE_NAME = "StartBenchmarkProcess";

    @Autowired
    private BenchmarkConfiguration config;

    @Autowired
    private ZeebeClient client;

    @Autowired
    private BenchmarkStartPiExceptionHandlingStrategy exceptionHandlingStrategy;

    @Autowired
    private ZeebeClientConfiguration zeebeClientConfiguration;

    @Autowired
    private MicrometerMetricsRecorder micrometerMetricsRecorder;

    private Map<String, Object> benchmarkPayload;
    
    // Partition pinning state
    private int targetPartition = -1;
    private int numericPodId = 0;

    @PostConstruct
    public void init() throws IOException {
        String variablesJsonString = tryReadVariables(config.getPayloadPath().getInputStream());
        variablesJsonString = variablesJsonString.replace("${RANDOM_UUID}", UUID.randomUUID().toString());
        benchmarkPayload = zeebeClientConfiguration.getJsonMapper().fromJsonAsMap(variablesJsonString);
        
        // Initialize partition pinning if enabled
        if (config.isEnablePartitionPinning()) {
            initializePartitionPinning();
        }
    }
    
    private void initializePartitionPinning() {
        if (config.getPodId() != null && !config.getPodId().isEmpty()) {
            try {
                numericPodId = Integer.parseInt(config.getPodId());
            } catch (NumberFormatException e) {
                // Try extracting from pod name format
                numericPodId = PartitionHashUtil.extractPodIdFromName(config.getPodId());
            }
        }
        
        targetPartition = PartitionHashUtil.getTargetPartitionForClient(
            numericPodId, config.getPartitionCount(), config.getReplicas());
            
        LOG.info("Partition pinning enabled: pod-id={}, target-partition={}, partition-count={}, replicas={}", 
                 numericPodId, targetPartition, config.getPartitionCount(), config.getReplicas());
    }

    public void startProcessInstance() {
        HashMap<Object, Object> variables = new HashMap<>();
        variables.putAll(this.benchmarkPayload);
        variables.put(BENCHMARK_START_DATE_MILLIS, Instant.now().toEpochMilli());
        variables.put(BENCHMARK_STARTER_ID, config.getStarterId());

        if (config.isEnablePartitionPinning()) {
            startProcessInstanceViaMessage(variables);
        } else {
            startProcessInstanceDirectly(variables);
        }
    }
    
    private void startProcessInstanceDirectly(HashMap<Object, Object> variables) {
        // Original logic for direct process instance creation
        FinalCommandStep createCommand = client.newCreateInstanceCommand()
                .bpmnProcessId(config.getBpmnProcessId())
                .latestVersion()
                .variables(variables);
        CommandWrapper command = new RefactoredCommandWrapper(
                createCommand,
                System.currentTimeMillis() + 5 * 60 * 1000, // 5 minutes
                "CreatePi" + config.getBpmnProcessId(),
                exceptionHandlingStrategy, micrometerMetricsRecorder);
        command.executeAsyncWithMetrics("PI_action","start",config.getBpmnProcessId());
    }
    
    private void startProcessInstanceViaMessage(HashMap<Object, Object> variables) {
        // Generate correlation key that hashes to our target partition
        String correlationKey;
        try {
            correlationKey = PartitionHashUtil.generateCorrelationKeyForPartition(
                targetPartition, config.getPartitionCount(), 1000);
        } catch (IllegalStateException e) {
            LOG.warn("Failed to generate correlation key for partition {}, using fallback", targetPartition, e);
            correlationKey = "benchmark-fallback-" + UUID.randomUUID().toString();
        }
        
        FinalCommandStep publishCommand = client.newPublishMessageCommand()
                .messageName(PARTITION_PINNING_MESSAGE_NAME)
                .correlationKey(correlationKey)
                .timeToLive(Duration.ofMinutes(5))
                .variables(variables);
                
        CommandWrapper command = new RefactoredCommandWrapper(
                publishCommand,
                System.currentTimeMillis() + 5 * 60 * 1000, // 5 minutes  
                "PublishMessage" + PARTITION_PINNING_MESSAGE_NAME,
                exceptionHandlingStrategy, micrometerMetricsRecorder);
        command.executeAsyncWithMetrics("PI_action","start_message",config.getBpmnProcessId());
        
        LOG.debug("Published message with correlation key {} targeting partition {}", 
                  correlationKey, targetPartition);
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
