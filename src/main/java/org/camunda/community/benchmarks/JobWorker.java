package org.camunda.community.benchmarks;

import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.ZeebeWorker;
import io.camunda.zeebe.spring.client.exception.ZeebeBpmnError;
import io.camunda.zeebe.spring.client.jobhandling.CommandWrapper;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.camunda.community.benchmarks.refactoring.RefactoredCommandWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class JobWorker {

    @Autowired
    private BenchmarkConfiguration config;

    @Autowired
    private BenchmarkCompleteJobExceptionHandlingStrategy exceptionHandlingStrategy;

    // TODO: Check if we can/need to check if the scheduler can catch up with all its work (or if it is overwhelmed)
    @Autowired
    private TaskScheduler scheduler;

    @Autowired
    private StatisticsCollector stats;

    @ZeebeWorker(type = "benchmark-task")
    public void handleSimpleyDelay(JobClient jobClient, ActivatedJob job) {
        // Auto-complete logic from https://github.com/camunda-community-hub/spring-zeebe/blob/ec41c5af1f64e512c8e7a8deea2aeacb35e61a16/client/spring-zeebe/src/main/java/io/camunda/zeebe/spring/client/jobhandling/JobHandlerInvokingSpringBeans.java#L24
        CompleteJobCommandStep1 completeCommand = jobClient.newCompleteCommand(job.getKey());
        CommandWrapper command = new RefactoredCommandWrapper(
                (FinalCommandStep) completeCommand,
                job.getDeadline(),
                job.toString(),
                exceptionHandlingStrategy);

        // schedule the completion asynchronously with the configured delay
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    command.executeAsync();
                    stats.incCompletedJobs();
                }
                catch (ZeebeBpmnError bpmnError) {
                    CommandWrapper command = new RefactoredCommandWrapper(
                            createThrowErrorCommand(jobClient, job, bpmnError),
                            job.getDeadline(),
                            job.toString(),
                            exceptionHandlingStrategy);
                    command.executeAsync();
                }
            }
        }, Instant.now().plusMillis(config.getTaskCompletionDelay()));
    }
    private FinalCommandStep<Void> createThrowErrorCommand(JobClient jobClient, ActivatedJob job, ZeebeBpmnError bpmnError) {
        FinalCommandStep<Void> command = jobClient.newThrowErrorCommand(job.getKey()) // TODO: PR for taking a job only in command chain
                .errorCode(bpmnError.getErrorCode())
                .errorMessage(bpmnError.getErrorMessage());
        return command;
    }
}
