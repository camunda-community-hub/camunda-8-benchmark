package org.camunda.community.benchmarks.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
@ConfigurationProperties(prefix = "benchmark")
@Getter
@Setter
public class BenchmarkConfiguration {

    private String starterId = "benchmarkStarter1";

    private boolean startProcesses = true;
    private long startPiPerSecond = 500;
    private String jobType = "benchmark-task";
    private int multipleJobTypes = 0;
    private boolean startWorkers = true;
    private long taskCompletionDelay = 200;
    private String bpmnProcessId = "benchmark";
    private Resource payloadPath; // = new UrlResource("classpath:bpmn/typical_payload.json");
    private Resource[] bpmnResource;
    private boolean autoDeployProcess = true;

    private String jobTypesToReplace;
    private String bpmnProcessIdToReplace;

    private long warmupPhaseDurationMillis = 0;
    private String startRateAdjustmentStrategy="backpressure";

    private double maxBackpressurePercentage = 10;
    private double startPiReduceFactor = 0.4;
    private double startPiIncreaseFactor = 0.4;

    private int taskPiRatio;

    private long fixedBackOffDelay = 0;
    
    private Resource messageScenario;
    private long messagesTtl;
    private long messagesScenariosPerSecond;
    private long delayBetweenMessages;
    private long messagesLoadDuration;

    // Partition pinning configuration
    private boolean enablePartitionPinning = false;
    private int partitionCount = 1;
    private int numberOfStarters = 1;
    private int correlationKeyMaxAttempts = 1000;

    //DI
    private boolean startDecisions = true;
    private long startDiPerSecond = 500;
    private String dmnDecisionId = "benchmark-decision";

    // Flow Control with Bucket4j
    private boolean flowControlEnabled = false;
    private long flowControlCapacity = 100;
    private long flowControlRefillTokens = 50;
    private long flowControlRefillPeriodMs = 1000;
    private int flowControlBackpressurePenalty = 20;
    private boolean flowControlRetryEnabled = false;
    private int flowControlMaxRetries = 5;
    private long flowControlInitialBackoffMs = 100;
}
