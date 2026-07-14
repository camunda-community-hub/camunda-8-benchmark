package org.camunda.community.benchmarks.utils;

import org.camunda.community.benchmarks.config.BenchmarkConfiguration;

import java.util.HashSet;
import java.util.Set;

/**
 * Derives the set of job types configured via {@code benchmark.jobType}/{@code multipleJobTypes},
 * independent of BPMN parsing (see {@link BpmnJobTypeParser} for the BPMN-discovered half).
 * <p>
 * Extracted out of {@code JobWorker} so both it and anything needing "how many job types does
 * this benchmark run per process instance" (e.g. a job-completion-rate-based rate strategy) share
 * one implementation instead of two that could drift apart.
 */
public final class JobTypeCounter {

    private JobTypeCounter() {
    }

    /**
     * @return the configured job type(s): a comma-separated {@code benchmark.jobType} list as-is,
     * or {@code <jobType>-1..N} when {@code benchmark.multipleJobTypes=N} is set, or the single
     * {@code jobType} otherwise.
     */
    public static Set<String> fromConfiguration(BenchmarkConfiguration config) {
        Set<String> jobTypes = new HashSet<>();
        String jobType = config.getJobType();

        if (jobType.contains(",")) {
            for (String job : jobType.split(",")) {
                jobTypes.add(job);
            }
        } else {
            int numberOfJobTypes = config.getMultipleJobTypes();
            if (numberOfJobTypes <= 0) {
                jobTypes.add(jobType);
            } else {
                for (int i = 0; i < numberOfJobTypes; i++) {
                    jobTypes.add(jobType + "-" + (i + 1));
                }
            }
        }

        return jobTypes;
    }
}
