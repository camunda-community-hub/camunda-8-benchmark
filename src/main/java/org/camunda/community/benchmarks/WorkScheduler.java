package org.camunda.community.benchmarks;

import io.camunda.zeebe.ResponseChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Syntax;

@Component
public class WorkScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(WorkScheduler.class);

    @Autowired
    private BenchmarkConfiguration config;

    @Autowired
    private StatisticsCollector stats;

    @Autowired
    private WorkExecutor executor;

    private long startTimeInMillis = System.currentTimeMillis();
    private long currentlyExecutedSecond = 0;
    private long piStartedInCurrentSecond = 0;
    private long counter = 0;

    private long batchSize = 1;
    private long howOften = 1; // every 1, 2 or 3rd row

    @PostConstruct
    public void init() {
        if (config.getStartPiPerSecond() < 100) {
            // we can handle this by starting one PI every x times 10 ms
            batchSize = 1;
            // better more than too less, then we can stop when we hit the limit
            howOften = Math.round( Math.floor( 100.0 / config.getStartPiPerSecond()) );
        } else {
            // we need to start batches every 10 ms
            howOften = 1;
            // better more than too less, then we can stop when we hit the limit
            batchSize = Math.round(Math.ceil( config.getStartPiPerSecond() / 100.0));
        }
        LOG.info("Configured benchmark to start " +config.getStartPiPerSecond() + " PIs per second. This means every " + howOften + " times of the 10ms intervals with a batch size of " + batchSize);
    }

    @Async
    @Scheduled(fixedRate = 10)
    public void kickOffAsynchronousWork() {
        long currentTime = System.currentTimeMillis();
        long passedTime = currentTime - startTimeInMillis;
        counter++;

        long processInstancesToStart = 0;

        // Every 10 ms we check what work we need to do
        // 10 ms is what the Java platform seems to be able to do reliably in different environments

        // if we still have time till the second is up
        if (passedTime < 1000) {
            // Check if we should start another batch
            if (counter % howOften == 0) {

                // now check if we still want to do the full batch
                if (piStartedInCurrentSecond + batchSize > config.getStartPiPerSecond()) {
                    // just start the remaining instances
                    processInstancesToStart = config.getStartPiPerSecond() - piStartedInCurrentSecond;
                    piStartedInCurrentSecond += processInstancesToStart;
                } else {
                    // start the batch size
                    processInstancesToStart = batchSize;
                    piStartedInCurrentSecond += batchSize;
                }
            }
            //LOG.info("Start " + processInstancesToStart);
        } else {
            // check if we have remaining process instances to start
            processInstancesToStart =  config.getStartPiPerSecond() - piStartedInCurrentSecond;
            //LOG.info("Start " + processInstancesToStart);

            // restart timer
            LOG.info("One second is over, resetting timer (Current count: " + stats.getStartedProcessInstances() + ")");
            piStartedInCurrentSecond = 0;
            startTimeInMillis = System.currentTimeMillis();
        }
        startProcessInstances( processInstancesToStart );
    }

    @Async
    private void startProcessInstances(long batchSize) {
        for (int i = 0; i < batchSize; i++) {
            executor.startProcessInstance();
            stats.incStartedProcessInstances();
        }
    }

}
