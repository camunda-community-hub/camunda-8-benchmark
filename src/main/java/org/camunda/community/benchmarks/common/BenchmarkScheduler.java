package org.camunda.community.benchmarks.common;

import jakarta.annotation.PostConstruct;
import org.camunda.community.benchmarks.StatisticsCollector;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

public abstract class BenchmarkScheduler extends RateCalculator {

  private static final Logger LOG = LoggerFactory.getLogger(BenchmarkScheduler.class);


  @Autowired
  protected StatisticsCollector stats;


  //running
  private long startTimeInMillis = System.currentTimeMillis();
  private long piStarted = 0;
  private long counter = 0;// every 1, 2 or 3rd row

  /**
   * Every 10 ms we check how many process instances we need to start
   * 10 ms is what the Java platform seems to be able to do reliably in different environments
   * Add some initial delay to give the workers time to connect
   */
  //@Async
  @Scheduled(fixedRate = 10, initialDelay = 5000)
  public void startBacthInstances() {

    long currentTime = System.currentTimeMillis();
    long passedTime = currentTime - startTimeInMillis;

    counter++;
    long processInstancesToStart = 0;

    // if we still have time till the second is up
    if (passedTime < 1000) {
      // Check if we should start another batch
      if (counter % howOften == 0) {
        // now check if we still want to do the full batch
        if (piStarted + batchSize > instanceStartingGoal) {
          // just start the remaining instances
          processInstancesToStart = instanceStartingGoal - piStarted;
          piStarted += processInstancesToStart;
        } else {
          // start the batch size
          processInstancesToStart = batchSize;
          piStarted += batchSize;
        }
      }
    } else {
      // check if we have remaining process instances to start
      processInstancesToStart = instanceStartingGoal - piStarted;

      // restart timer
      LOG.debug("One second is over, resetting timer");
      piStarted = 0;
      startTimeInMillis = System.currentTimeMillis();
    }
    // start after all calculations to avoid that the next scheduler run intervenes.
    // (the above calculations should always be faster than 10ms, starting a big batch might not)
    // TODO: Think about if we should detect if starting takes longer than the 10ms interval
    startInstances( processInstancesToStart );
  }

  @Async
  protected abstract void startInstances(long batchSize);

}
