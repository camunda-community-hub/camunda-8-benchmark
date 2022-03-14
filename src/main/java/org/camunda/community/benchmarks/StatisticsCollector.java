package org.camunda.community.benchmarks;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class StatisticsCollector {

    private Date startTime = new Date();

    private long startedProcessInstances;
    private long completedProcessInstances;
    private long completedJobs;

    @Scheduled(fixedRate = 1000)
    public void prinStatus() {
        System.out.println("Started process instances: " + startedProcessInstances);
    }

    public void incStartedProcessInstances() {
        startedProcessInstances++;
    }


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

}
