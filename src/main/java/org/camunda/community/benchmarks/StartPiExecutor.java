package org.camunda.community.benchmarks;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.response.PublishMessageResponse;
import io.camunda.client.jobhandling.CommandWrapper;
import io.camunda.client.metrics.MicrometerMetricsRecorder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.camunda.community.benchmarks.common.BenchmarkExecutor;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.camunda.community.benchmarks.partition.PartitionHashUtil;
import org.camunda.community.benchmarks.refactoring.RefactoredCommandWrapper;
import org.camunda.community.benchmarks.strategy.BenchmarkStartPiExceptionHandlingStrategy;
import org.camunda.community.benchmarks.resilience.ResilientCamundaStarter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class StartPiExecutor extends BenchmarkExecutor {

    private static final Logger LOG = LogManager.getLogger(StartPiExecutor.class);
    
    public static final String BENCHMARK_START_DATE_MILLIS = "benchmark_start_date_millis";
    public static final Object BENCHMARK_STARTER_ID = "benchmark_starter_id";
    
    // Message name for partition pinning
    private static final String PARTITION_PINNING_MESSAGE_NAME = "StartBenchmarkProcess";

    @Autowired
    private BenchmarkConfiguration config;

    @Autowired
    private CamundaClient client;

    @Autowired
    private BenchmarkStartPiExceptionHandlingStrategy exceptionHandlingStrategy;

    @Autowired
    private CamundaClientConfiguration zeebeClientConfiguration;

    @Autowired
    private MicrometerMetricsRecorder micrometerMetricsRecorder;

    @Autowired(required = false)
    private ResilientCamundaStarter resilientStarter;

    private Map<String, Object> benchmarkPayload;
    
    // Partition pinning state
    private int[] targetPartitions = new int[0];
    private int numericClientId = 0;
    
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
        String starterId = config.getStarterId();
        if (starterId != null && !starterId.isEmpty()) {
            try {
                numericClientId = Integer.parseInt(starterId);
            } catch (NumberFormatException e) {
                // Try extracting from starter name format
                numericClientId = PartitionHashUtil.extractClientIdFromName(starterId);
            }
        }
        
        targetPartitions = PartitionHashUtil.getTargetPartitionsForClient(
            numericClientId, config.getPartitionCount(), config.getNumberOfStarters());
            
        LOG.info("Partition Pinning enabled: partition-count={}, numberOfStarters={}, starterId={}, numericClientId={}, target-partitions={}", 
                 config.getPartitionCount(), config.getNumberOfStarters(), starterId, numericClientId, java.util.Arrays.toString(targetPartitions));
    }

    @Override
    public void startInstance() {
      HashMap<Object, Object> variables = new HashMap<>(this.benchmarkPayload);
        variables.put(BENCHMARK_START_DATE_MILLIS, Instant.now().toEpochMilli());
        variables.put(BENCHMARK_STARTER_ID, config.getStarterId());

        if (config.isEnablePartitionPinning()) {
            startProcessInstanceViaMessage(variables);
        } else {
            startProcessInstanceDirectly(variables);
        }
    }
    
    private void startProcessInstanceDirectly(HashMap<Object, Object> variables) {
        // Use resilient starter if enabled, otherwise use the traditional approach
        if (config.isResilienceEnabled() && resilientStarter != null) {
            startProcessInstanceWithResilience(variables);
        } else {
            startProcessInstanceWithCommandWrapper(variables);
        }
    }
    
    private void startProcessInstanceWithResilience(HashMap<Object, Object> variables) {
        try {
            // Convert HashMap<Object, Object> to Map<String, Object>
            @SuppressWarnings("unchecked")
            Map<String, Object> stringVariables = (Map<String, Object>) (Map<?, ?>) variables;
            
            // Use the ResilientCamundaStarter with rate limiting and retry
            resilientStarter.startProcessInstanceAsync(
                config.getBpmnProcessId(), 
                stringVariables
            ).whenComplete((event, error) -> {
                if (error != null) {
                    LOG.error("Failed to start process instance with resilience", error);
                } else {
                    LOG.debug("Started process instance with key: {}", event.getProcessInstanceKey());
                }
            });
        } catch (Exception e) {
            LOG.error("Exception in resilient process start", e);
        }
    }
    
    private void startProcessInstanceWithCommandWrapper(HashMap<Object, Object> variables) {
        // Auto-complete logic from https://github.com/camunda-community-hub/spring-zeebe/blob/ec41c5af1f64e512c8e7a8deea2aeacb35e61a16/client/spring-zeebe/src/main/java/io/camunda/zeebe/spring/client/jobhandling/JobHandlerInvokingSpringBeans.java#L24
        FinalCommandStep<ProcessInstanceEvent> createCommand = client.newCreateInstanceCommand()
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
        // Select a random partition from our target partitions for load balancing
        int partition = PartitionHashUtil.selectRandomPartition(targetPartitions);
        
        // Generate correlation key that hashes to our selected partition
        String correlationKey;
            int maxAttempts = config.getCorrelationKeyMaxAttempts() * config.getPartitionCount();
            try {
                correlationKey = PartitionHashUtil.generateCorrelationKeyForPartition(
                    partition, config.getPartitionCount(), maxAttempts);
                LOG.debug("Generated correlation key '{}' for partition {}", correlationKey, partition);
            } catch (IllegalStateException e) {
            LOG.warn("Failed to generate correlation key for partition {}, using fallback", partition, e);
            correlationKey = "benchmark-fallback-p" + partition + "-" + UUID.randomUUID().toString();
        }
        
        FinalCommandStep<PublishMessageResponse> publishCommand = client.newPublishMessageCommand()
                .messageName(PARTITION_PINNING_MESSAGE_NAME)
                .correlationKey(correlationKey)
                .timeToLive(Duration.ofMinutes(config.getMessagesTtl()))
                .variables(variables);
                
        CommandWrapper command = new RefactoredCommandWrapper(
                publishCommand,
                System.currentTimeMillis() + 5 * 60 * 1000, // 5 minutes  
                "PublishMessage" + PARTITION_PINNING_MESSAGE_NAME,
                exceptionHandlingStrategy, micrometerMetricsRecorder);
        command.executeAsyncWithMetrics("PI_action","start_message",config.getBpmnProcessId());
        
        LOG.debug("Published message with correlation key {} targeting partition {}", 
                  correlationKey, partition);
    }
}
