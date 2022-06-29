package org.camunda.community.benchmarks;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.zeebe.spring.client.annotation.ZeebeWorker;
import io.camunda.zeebe.spring.client.exception.ZeebeBpmnError;
import io.camunda.zeebe.spring.client.jobhandling.CommandWrapper;
import io.camunda.zeebe.spring.client.jobhandling.JobHandlerInvokingSpringBeans;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.camunda.community.benchmarks.refactoring.RefactoredCommandWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;
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
    private ZeebeClient client;

    @Autowired
    private StatisticsCollector stats;

    private void registerWorker(String jobType) {

        long fixedBackOffDelay = config.getFixedBackOffDelay();

        JobWorkerBuilderStep1.JobWorkerBuilderStep3 step3 = client.newWorker()
                .jobType(jobType)
                .handler(new SimpleDelayCompletionHandler(false));

        if(fixedBackOffDelay > 0) {
            step3.backoffSupplier(new FixedBackoffSupplier(fixedBackOffDelay));
        }

        step3.open();
    }

    // Don't do @PostConstruct as this is too early in the Spring lifecycle
    //@PostConstruct
    public void startWorkers() {
        String taskType = config.getJobType();

        int numberOfJobTypes = config.getMultipleJobTypes();

        if(numberOfJobTypes <= 0) {

            // worker for normal task type
            registerWorker(taskType);

            // worker for normal "task-type-{starterId}"
            registerWorker(taskType + "-" + config.getStarterId());

            // worker marking completion of process instance via "task-type-complete"
            registerWorker(taskType + "-completed");

            // worker marking completion of process instance via "task-type-complete"
            registerWorker(taskType + "-" + config.getStarterId() + "-completed");

        } else {

            for(int i=0; i<numberOfJobTypes; i++) {
                registerWorker(taskType + "-" + (i+1));
            }

        }

    }

    public class SimpleDelayCompletionHandler implements JobHandler {

        private boolean markProcessInstanceCompleted;

        public SimpleDelayCompletionHandler(boolean markProcessInstanceCompleted) {
            this.markProcessInstanceCompleted = markProcessInstanceCompleted;
        }

        @Override
        public void handle(JobClient jobClient, ActivatedJob job) throws Exception {
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
                        if (markProcessInstanceCompleted) {
                            Object startEpochMillis = job.getVariablesAsMap().get(StartPiExecutor.BENCHMARK_START_DATE_MILLIS);
                            if (startEpochMillis!=null && startEpochMillis instanceof Long) {
                                stats.incCompletedProcessInstances((Long)startEpochMillis, Instant.now().toEpochMilli());
                            } else {
                                stats.incCompletedProcessInstances();
                            }
                        }
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
    }

    private FinalCommandStep<Void> createThrowErrorCommand(JobClient jobClient, ActivatedJob job, ZeebeBpmnError bpmnError) {
        FinalCommandStep<Void> command = jobClient.newThrowErrorCommand(job.getKey()) // TODO: PR for taking a job only in command chain
                .errorCode(bpmnError.getErrorCode())
                .errorMessage(bpmnError.getErrorMessage());
        return command;
    }
}
