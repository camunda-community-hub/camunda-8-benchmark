package org.camunda.community.benchmarks;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

@Component
public class StatisticsCollector {

    private Date startTime = new Date();

    private MetricRegistry metricRegistry = new MetricRegistry();

    private long lastPrintStartedProcessInstances = 0;
    private long lastPrintCompletedProcessInstances = 0;
    private long lastPrintCompletedJobs = 0;
    private long lastPrintStartedProcessInstancesBackpressure = 0;
    private long lastPrintCompletedJobsBackpressure = 0;
    private long piPerSecondGoal;

    @Scheduled(fixedRate = 5000)
    public void printStatus() {
        System.out.println("------------------- " + Instant.now() + " Current goal (PI/s): " + piPerSecondGoal);

        long count = getStartedPiMeter().getCount();
        System.out.println("STARTED PI:     " + f(count) + " (+ " + f(count-lastPrintStartedProcessInstances) + ")");
        lastPrintStartedProcessInstances = count;

        long backpressure = getBackpressureOnStartPiMeter().getCount();
        System.out.println("Backpressure:   " + f(backpressure) + " (+ " + f(backpressure - lastPrintStartedProcessInstancesBackpressure) + ") Last minute rate: " + String.format("%.2f", getBackpressureOnStartPiMeter().getOneMinuteRate()));
        lastPrintStartedProcessInstancesBackpressure = backpressure;

        count = getCompletedJobsMeter().getCount();
        System.out.println("COMPLETED JOBS: " + f(count) + " (+ " + f(count-lastPrintCompletedJobs) + ")");
        lastPrintCompletedJobs = count;

        backpressure = getBackpressureOnJobCompleteMeter().getCount();
        System.out.println("Backpressure:   " + f(backpressure) + " (+ " + f(backpressure - lastPrintCompletedJobsBackpressure) + ")");
        lastPrintCompletedJobsBackpressure = backpressure;
    }

    public String f(Number n) {
        return String.format("%1$5s", n);
    }

    public Meter getStartedPiMeter() {
        return metricRegistry.meter("startedPi" );
    }

    public Meter getCompletedJobsMeter() {
        return metricRegistry.meter("completedJobs" );
    }

    public Meter getBackpressureOnJobCompleteMeter() {
        return metricRegistry.meter("backpressureOnJobCompleteMeter" );
    }

    public Meter getBackpressureOnStartPiMeter() {
        return metricRegistry.meter("backpressureOnStartPiMeter" );
    }

    public void incStartedProcessInstances() {
        getStartedPiMeter().mark();
    }

    public void incStartedProcessInstancesBackpressure() {
        getBackpressureOnStartPiMeter().mark();
    }

    public void incCompletedJobs() {
        getCompletedJobsMeter().mark();
    }

    public void hintOnNewPiPerSecondGoald(long piPerSecondGoal) {
        this.piPerSecondGoal = piPerSecondGoal;
    }
/*
    public Date getStartTime() {
        return startTime;
    }

    public long getStartedProcessInstances() {
        return startedProcessInstances;
    }

    public long getCompletedProcessInstances() {
        return completedProcessInstances;
    }

    public long getCompletedJobs() {
        return completedJobs;
    }
*/
}
