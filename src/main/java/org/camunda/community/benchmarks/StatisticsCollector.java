package org.camunda.community.benchmarks;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import io.prometheus.client.CollectorRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Component
public class StatisticsCollector {

    private Date startTime = new Date();

    // Leveraging Dropwizard here to do rate calculation within the app
    private com.codahale.metrics.MetricRegistry dropwizardMetricRegistry = new com.codahale.metrics.MetricRegistry();
    // But also the more modern Micrometer library, which can be exported to prometheus easily (using Spring Actuator)
    @Autowired
    private io.micrometer.core.instrument.MeterRegistry micrometerMetricRegistry;

    private long lastPrintStartedProcessInstances = 0;
    private long lastPrintCompletedProcessInstances = 0;
    private long lastPrintCompletedJobs = 0;
    private long lastPrintStartedProcessInstancesBackpressure = 0;
    private long lastPrintCompletedJobsBackpressure = 0;

    private long piPerSecondGoal;

    @Scheduled(fixedRate = 10*1000)
    public void printStatus() {
        System.out.println("------------------- " + Instant.now() + " Current goal (PI/s): " + piPerSecondGoal);

        long count = getStartedPiMeter().getCount();
        System.out.println("STARTED PI:     " + f(count) + " (+ " + f(count-lastPrintStartedProcessInstances) + ") Last minute rate: " + f(getStartedPiMeter().getOneMinuteRate()));
        lastPrintStartedProcessInstances = count;

        long backpressure = getBackpressureOnStartPiMeter().getCount();
        System.out.println("Backpressure:   " + f(backpressure) + " (+ " + f(backpressure - lastPrintStartedProcessInstancesBackpressure) + ") Last minute rate: " + f(getBackpressureOnStartPiMeter().getOneMinuteRate()) + ". Percentage: " + fpercent(getBackpressureOnStartPercentage()) + " %");
        lastPrintStartedProcessInstancesBackpressure = backpressure;

        count = getCompletedJobsMeter().getCount();
        System.out.println("COMPLETED JOBS: " + f(count) + " (+ " + f(count-lastPrintCompletedJobs) + ") Last minute rate: " + f(getCompletedJobsMeter().getOneMinuteRate()));
        lastPrintCompletedJobs = count;

        backpressure = getBackpressureOnJobCompleteMeter().getCount();
        System.out.println("Backpressure:   " + f(backpressure) + " (+ " + f(backpressure - lastPrintCompletedJobsBackpressure) + ")");
        lastPrintCompletedJobsBackpressure = backpressure;
    }

    public String fpercent(double n) {
        return String.format("%5.3f", n);
    }
    public String f(double n) {
        return String.format("%5.1f", n);
    }
    public String f(long n) {
        return String.format("%1$5s", n);
    }

    public double getBackpressureOnStartPercentage() {
        return getBackpressureOnStartPiMeter().getOneMinuteRate() / getStartedPiMeter().getOneMinuteRate() * 100;
    }

    public Meter getStartedPiMeter() {
        return dropwizardMetricRegistry.meter("startedPi" );
    }

    public Meter getCompletedJobsMeter() {
        return dropwizardMetricRegistry.meter("completedJobs" );
    }

    public Meter getBackpressureOnJobCompleteMeter() {
        return dropwizardMetricRegistry.meter("backpressureOnJobCompleteMeter" );
    }

    public Meter getBackpressureOnStartPiMeter() {
        return dropwizardMetricRegistry.meter("backpressureOnStartPiMeter" );
    }

    public void incStartedProcessInstances() {
        getStartedPiMeter().mark();
        micrometerMetricRegistry.counter("startedPi").increment();
    }

    public void incStartedProcessInstancesBackpressure() {
        getBackpressureOnStartPiMeter().mark();
        micrometerMetricRegistry.counter("backpressureOnStartPiMeter").increment();
    }

    public void incCompletedJobs() {
        getCompletedJobsMeter().mark();
        micrometerMetricRegistry.counter("completedJobs").increment();
    }

    public void hintOnNewPiPerSecondGoald(long piPerSecondGoal) {
        this.piPerSecondGoal = piPerSecondGoal;
    }
}
