package org.camunda.community.benchmarks.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.concurrent.TimeUnit;

@Configuration
@ConfigurationProperties(prefix = "benchmark")
public class BenchmarkConfiguration {

    private String starterId = "benchmarkStarter1";

    private long startPiPerSecond = 500;
    private String jobType = "benchmark-task";
    private int multipleJobTypes = 0;
    private long taskCompletionDelay = 200;
    private String bpmnProcessId = "benchmark";
    private String payloadPath = "bpmn/typical_payload.json";
    private Resource[] bpmnResource;
    private boolean autoDeployProcess = true;

    private long warmupPhaseDurationMillis = 0;
    private String startRateAdjustmentStrategy="backpressure";

    private double maxBackpressurePercentage = 10;
    private double startPiReduceFactor = 0.4;
    private double startPiIncreaseFactor = 0.4;

    private int taskPiRatio;

    private long fixedBackOffDelay = 0;

    public String getStartRateAdjustmentStrategy() {
        return startRateAdjustmentStrategy;
    }

    public void setStartRateAdjustmentStrategy(String startRateAdjustmentStrategy) {
        this.startRateAdjustmentStrategy = startRateAdjustmentStrategy;
    }

    public long getWarmupPhaseDurationMillis() {
        return warmupPhaseDurationMillis;
    }

    public void setWarmupPhaseDurationMillis(long warmupPhaseDurationMillis) {
        this.warmupPhaseDurationMillis = warmupPhaseDurationMillis;
    }

    public int getTaskPiRatio() {
        return taskPiRatio;
    }

    public void setTaskPiRatio(int taskPiRatio) {
        this.taskPiRatio = taskPiRatio;
    }

    public String getPayloadPath() {
        return payloadPath;
    }

    public void setPayloadPath(String payloadPath) {
        this.payloadPath = payloadPath;
    }

    public long getTaskCompletionDelay() {
        return taskCompletionDelay;
    }

    public void setTaskCompletionDelay(long taskCompletionDelay) {
        this.taskCompletionDelay = taskCompletionDelay;
    }


    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public int getMultipleJobTypes() {
        return multipleJobTypes;
    }

    public void setMultipleJobTypes(int multipleJobTypes) {
        this.multipleJobTypes = multipleJobTypes;
    }

    public long getStartPiPerSecond() {
        return startPiPerSecond;
    }

    public void setStartPiPerSecond(long startPiPerSecond) {
        this.startPiPerSecond = startPiPerSecond;
    }

    public void setBpmnProcessId(String bpmnProcessId) {
        this.bpmnProcessId = bpmnProcessId;
    }

    public String getBpmnProcessId() {
        return bpmnProcessId;
    }

    public double getMaxBackpressurePercentage() {
        return maxBackpressurePercentage;
    }

    public void setMaxBackpressurePercentage(double maxBackpressurePercentage) {
        this.maxBackpressurePercentage = maxBackpressurePercentage;
    }

    public double getStartPiReduceFactor() {
        return startPiReduceFactor;
    }

    public double getStartPiIncreaseFactor() {
        return startPiIncreaseFactor;
    }

    public void setStartPiIncreaseFactor(double startPiIncreaseFactor) {
        this.startPiIncreaseFactor = startPiIncreaseFactor;
    }

    public boolean isAutoDeployProcess() {
        return autoDeployProcess;
    }

    public void setAutoDeployProcess(boolean autoDeployProcess) {
        this.autoDeployProcess = autoDeployProcess;
    }

    public Resource[] getBpmnResource() {
        return bpmnResource;
    }

    public void setBpmnResource(Resource[] bpmnResource) {
        this.bpmnResource = bpmnResource;
    }

    public void setStartPiReduceFactor(double startPiReduceFactor) {
        this.startPiReduceFactor = startPiReduceFactor;
    }

    public String getStarterId() {
        return starterId;
    }

    public void setStarterId(String starterId) {
        this.starterId = starterId;
    }

    public long getFixedBackOffDelay() {
        return fixedBackOffDelay;
    }

    public void setFixedBackOffDelay(long fixedBackOffDelay) {
        this.fixedBackOffDelay = fixedBackOffDelay;
    }
}
