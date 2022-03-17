package org.camunda.community.benchmarks;

import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;

@Component
public class StartPiScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(StartPiScheduler.class);

    @Autowired
    private BenchmarkConfiguration config;

    @Autowired
    private StatisticsCollector stats;

    @Autowired
    private StartPiExecutor executor;

    private long startTimeInMillis = System.currentTimeMillis();
    private long piStarted = 0;
    private long piStartedGoal = 0;
    private long counter = 0;

    private long batchSize = 1;
    private long howOften = 1; // every 1, 2 or 3rd row

    @PostConstruct
    public void init() {
        calculateParameters(config.getStartPiPerSecond());
    }

    public void calculateParameters(long piPerSecondGoal) {
        this.piStartedGoal = piPerSecondGoal;
        stats.hintOnNewPiPerSecondGoald(piPerSecondGoal);
        if (config.getStartPiPerSecond() < 100) {
            // we can handle this by starting one PI every x times 10 ms
            batchSize = 1;
            // better more than too less, then we can stop when we hit the limit
            howOften = Math.round( Math.floor( 100.0 / piPerSecondGoal) );
        } else {
            // we need to start batches every 10 ms
            howOften = 1;
            // better more than too less, then we can stop when we hit the limit
            batchSize = Math.round(Math.ceil( piPerSecondGoal / 100.0));
        }
        LOG.info("Configured benchmark to start " + piPerSecondGoal + " PIs per second. This means every " + howOften + " times of the 10ms intervals with a batch size of " + batchSize);
    }

    /**
     * Every 10 ms we check how many process instances we need to start
     * 10 ms is what the Java platform seems to be able to do reliably in different environments
     * Add some initial delay to give the workers time to connect
     */
    @Async
    @Scheduled(fixedRate = 10, initialDelay = 5000)
    public void startSomeProcessInstances() {
        long currentTime = System.currentTimeMillis();
        long passedTime = currentTime - startTimeInMillis;

        counter++;
        long processInstancesToStart = 0;

        // if we still have time till the second is up
        if (passedTime < 1000) {
            // Check if we should start another batch
            if (counter % howOften == 0) {
                // now check if we still want to do the full batch
                if (piStarted + batchSize > piStartedGoal) {
                    // just start the remaining instances
                    processInstancesToStart = piStartedGoal - piStarted;
                    piStarted += processInstancesToStart;
                } else {
                    // start the batch size
                    processInstancesToStart = batchSize;
                    piStarted += batchSize;
                }
            }
        } else {
            // check if we have remaining process instances to start
            processInstancesToStart = piStartedGoal - piStarted;

            // restart timer
            LOG.debug("One second is over, resetting timer");
            piStarted = 0;
            startTimeInMillis = System.currentTimeMillis();
        }
        // start after all calculations to avoid that the next scheduler run intervenes.
        // (the above calculations should always be faster than 10ms, starting a big batch might not)
        // TODO: Think about if we should detect if starting takes longer than the 10ms interval
        startProcessInstances( processInstancesToStart );
    }

    @Async
    private void startProcessInstances(long batchSize) {
        for (int i = 0; i < batchSize; i++) {
            executor.startProcessInstance();
            stats.incStartedProcessInstances();
        }
    }

    private static final double BACKPRESSURE_MAX_RATE_PER_SECOND_BIG = 15; // TODO: use percentage
    private static final long BACKPRESSURE_ADJUSTEMENT_AMOUNT_BIG = 10;

    private static final double BACKPRESSURE_MAX_RATE_PER_SECOND_SMALL = 3;
    private static final long BACKPRESSURE_ADJUSTEMENT_AMOUNT_SMALL = 1;

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void hintBackpressureReceived() {
        if (stats.getBackpressureOnStartPiMeter().getOneMinuteRate() > BACKPRESSURE_MAX_RATE_PER_SECOND_BIG) {
            // Backpressure is considered much too high if above
            adjustStartRateBy(-1 * BACKPRESSURE_ADJUSTEMENT_AMOUNT_BIG);
        } else if (stats.getBackpressureOnStartPiMeter().getOneMinuteRate() > BACKPRESSURE_MAX_RATE_PER_SECOND_SMALL) {
            // Backpressure is still a bit too high
            // too much backpressure - reduce start rate by fixed amount //(by 1%)
            adjustStartRateBy(-1 * BACKPRESSURE_ADJUSTEMENT_AMOUNT_SMALL); //Math.round(Math.ceil(piStartedGoal/100));
        } else if (stats.getBackpressureOnStartPiMeter().getOneMinuteRate() < 1) {
            // increase it again
            adjustStartRateBy(BACKPRESSURE_ADJUSTEMENT_AMOUNT_SMALL); //Math.round(Math.ceil(piStartedGoal/100));
        }
    }

    private void adjustStartRateBy(long amount) {
        long newGoal =  piStartedGoal + amount;
        if (newGoal<=0) {
            newGoal = 1;
        }
        LOG.info("Backpressure rate (" + stats.getBackpressureOnStartPiMeter().getOneMinuteRate() + ") leads to change in start rate by "+amount+" to " + newGoal);
        calculateParameters(newGoal);
    }
}
