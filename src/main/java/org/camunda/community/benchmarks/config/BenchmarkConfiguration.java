package org.camunda.community.benchmarks.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@ConfigurationProperties(prefix = "benchmark")
public class BenchmarkConfiguration {

    private long startPiPerSecond = 500;
    private String jobType = "benchmark-task";
    private long taskCompletionDelay = 200;
    private String bpmnProcessId = "benchmark";
    private String payloadPath = "bpmn/typical_payload.json";

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
}
