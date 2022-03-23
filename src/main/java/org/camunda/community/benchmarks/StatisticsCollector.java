package org.camunda.community.benchmarks;

import com.codahale.metrics.Meter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    private long piPerSecondGoal;

    @Scheduled(fixedRate = 10*1000)
    public void printStatus() {
        System.out.println("------------------- " + Instant.now() + " Current goal (PI/s): " + piPerSecondGoal);

        long count = getStartedPiMeter().getCount();
        long backpressure = getBackpressureOnStartPiMeter().getCount();
        System.out.println("PI STARTED:     " + f(count) + " (+ " + f(count-lastPrintStartedProcessInstances) + ") Last minute rate: " + f(getStartedPiMeter().getOneMinuteRate()));
        System.out.println("  Backpressure: " + f(backpressure) + " (+ " + f(backpressure - lastPrintStartedProcessInstancesBackpressure) + ") Last minute rate: " + f(getBackpressureOnStartPiMeter().getOneMinuteRate()) + ". Percentage: " + fpercent(getBackpressureOnStartPercentage()) + " %");
        lastPrintStartedProcessInstances = count;
        lastPrintStartedProcessInstancesBackpressure = backpressure;

        count = getCompletedProcessInstancesMeter().getCount();
        System.out.println("PI COMPLETED:   " + f(count) + " (+ " + f(count-lastPrintCompletedProcessInstances) + ") Last minute rate: " + f(getCompletedProcessInstancesMeter().getOneMinuteRate()));
        lastPrintCompletedProcessInstances = count;

        count = getCompletedJobsMeter().getCount();
        System.out.println("COMPLETED JOBS: " + f(count) + " (+ " + f(count-lastPrintCompletedJobs) + ") Last minute rate: " + f(getCompletedJobsMeter().getOneMinuteRate()));
        lastPrintCompletedJobs = count;

        /*backpressure = getBackpressureOnJobCompleteMeter().getCount();
        System.out.println("Backpressure:   " + f(backpressure) + " (+ " + f(backpressure - lastPrintCompletedJobsBackpressure) + ")");
        lastPrintCompletedJobsBackpressure = backpressure;
        */
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
        return dropwizardMetricRegistry.meter("pi_started" );
    }
    public Meter getCompletedJobsMeter() {
        return dropwizardMetricRegistry.meter("jobs_completed" );
    }
    public Meter getCompletedProcessInstancesMeter() {
        return dropwizardMetricRegistry.meter("pi_completed" );
    }
    public Meter getBackpressureOnStartPiMeter() {
        return dropwizardMetricRegistry.meter("pi_backpressure" );
    }

    public void hintOnNewPiPerSecondGoald(long piPerSecondGoal) {
        this.piPerSecondGoal = piPerSecondGoal;
    }

    public void incStartedProcessInstances() {
        getStartedPiMeter().mark();
        micrometerMetricRegistry.counter("pi_started").increment();
    }

    public void incStartedProcessInstancesBackpressure() {
        getBackpressureOnStartPiMeter().mark();
        micrometerMetricRegistry.counter("pi_backpressure").increment();
    }

    public void incCompletedProcessInstances() {
        getCompletedProcessInstancesMeter().mark();
        micrometerMetricRegistry.counter("pi_completed").increment();
    }
    public void incCompletedProcessInstances(long startMillis, long endMillis) {
        incCompletedProcessInstances();
        micrometerMetricRegistry.timer("pi_cycletime").record(endMillis - startMillis, TimeUnit.MILLISECONDS);
    }

    public void incCompletedJobs() {
        getCompletedJobsMeter().mark();
        micrometerMetricRegistry.counter("jobs_completed").increment();
    }

    public void incStartedProcessInstancesException(String exceptionMessage) {
        micrometerMetricRegistry.counter("pi_exception", "exception", exceptionMessage).increment();
    }

    public void incCompletedJobsException(String exceptionMessage) {
        micrometerMetricRegistry.counter("jobs_exception", "exception", exceptionMessage).increment();
    }


}
