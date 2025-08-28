package org.camunda.community.benchmarks;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.camunda.zeebe.spring.client.actuator.MicrometerMetricsRecorder;
import io.camunda.zeebe.spring.common.exception.ZeebeBpmnError;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.camunda.community.benchmarks.refactoring.RefactoredCommandWrapper;
import org.camunda.community.benchmarks.utils.BpmnJobTypeParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.zeebe.spring.client.jobhandling.CommandWrapper;

@Component
public class JobWorker {
    private static final Logger LOG = LogManager.getLogger(JobWorker.class);

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

    @Autowired
    private MicrometerMetricsRecorder micrometerMetricsRecorder;

    // currently only used when multipleJobTypes > 0
    // TODO make configurable to allow combinations with other features
    private String lastJobType;

    // Package-private for testing
    void registerWorker(String jobType, Boolean markPiCompleted) {

        long fixedBackOffDelay = config.getFixedBackOffDelay();

        JobWorkerBuilderStep1.JobWorkerBuilderStep3 step3 = client.newWorker()
                .jobType(jobType)
                .handler(new SimpleDelayCompletionHandler(markPiCompleted))
                .name(jobType);

        if(fixedBackOffDelay > 0) {
            step3.backoffSupplier(new FixedBackoffSupplier(fixedBackOffDelay));
        }

        step3.open();
        stats.registerJobTypeTimer(jobType);
    }

    // Don't do @PostConstruct as this is too early in the Spring lifecycle
    //@PostConstruct
    public void startWorkers() {
        if (!config.isStartWorkers()) {
            LOG.info("Job workers are disabled, skipping worker registration");
            return;
        }

        Set<String> bpmnJobTypes = new HashSet<>();
        Set<String> configJobTypes = new HashSet<>();
        
        // Extract job types from BPMN files if available
        if (config.isAutoDeployProcess()
            && config.getBpmnResource() != null
            && config.getBpmnResource().length > 0) {
            bpmnJobTypes = BpmnJobTypeParser.extractJobTypes(config.getBpmnResource());
        }

        // Always extract job types from configuration
        configJobTypes = getJobTypesFromConfiguration();
        LOG.info("Found {} job types from configuration: {}", configJobTypes.size(), configJobTypes);

        // Register workers for configuration-based job types
        // Only use registerWorkersForTaskType (with variants) when there is exactly one config job type
        // Otherwise use simple registerWorker to avoid conflicts with BPMN-discovered job types
        if (configJobTypes.size() == 1 && config.getMultipleJobTypes() <= 0) {
            registerWorkersForTaskType(config.getJobType());
        } else {
            for (String jobType : configJobTypes) {
                String effectiveJobType = applyPartitionPinning(jobType);
                registerWorker(effectiveJobType, jobType.equals(lastJobType));
            }
        }
        
        // Register simple workers for BPMN-discovered job types that are NOT in configuration
        Set<String> bpmnOnlyJobTypes = new HashSet<>(bpmnJobTypes);
        LOG.info("BPMN job types before removeAll: {}", bpmnOnlyJobTypes);
        LOG.info("Config job types to remove: {}", configJobTypes);
        bpmnOnlyJobTypes.removeAll(configJobTypes);
        LOG.info("BPMN job types after removeAll: {}", bpmnOnlyJobTypes);
        
        for (String jobType : bpmnOnlyJobTypes) {
            String effectiveTaskType = applyPartitionPinning(jobType);
            registerWorker(effectiveTaskType, false);
        }
        
        int totalWorkers = configJobTypes.size() + bpmnOnlyJobTypes.size();
        String configDescription = configJobTypes.size() == 1 ? "from config with variants" : "from config with simple registration";
        LOG.info("Registered job workers for {} job types ({} {}, {} from BPMN only)", 
            totalWorkers, configJobTypes.size(), configDescription, bpmnOnlyJobTypes.size());
    }

    /**
     * Applies partition pinning to a job type if enabled.
     * @param jobType The original job type
     * @return The effective job type with starter ID prefix if partition pinning is enabled
     */
    private String applyPartitionPinning(String jobType) {
        if (config.isEnablePartitionPinning()) {
            String starterId = config.getStarterId();
            if (starterId != null && !starterId.isEmpty()) {
                String effectiveJobType = starterId + "-" + jobType;
                LOG.info("Partition pinning enabled: registering workers for task type with starter ID prefix: {}", effectiveJobType);
                return effectiveJobType;
            }
        }
        return jobType;
    }

    /**
     * Gets job types from configuration properties (fallback method).
     */
    private Set<String> getJobTypesFromConfiguration() {
        Set<String> jobTypes = new HashSet<>();
        String jobType = config.getJobType();

        // If the job types are listed out then loop through the list of jobTypes and create
        // Otherwise generate the jobtypes automatically based on the multipleJobTypes property
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
                    String jobTypeWithIndex = jobType + "-" + (i + 1);
                    jobTypes.add(jobTypeWithIndex);
                    if (i == numberOfJobTypes - 1) {
                        this.lastJobType = jobTypeWithIndex;
                    }
                }
            }
        }

        return jobTypes;
    }

    private void registerWorkersForTaskType(String taskType) {
        
        // worker for normal task type
        registerWorker(taskType, false);
        
        // worker marking completion of process instance via "task-type-complete"
        registerWorker(taskType + "-completed", true);

        if (config.getStarterId() != null && !config.getStarterId().isEmpty()) {
            // worker for normal "task-type-{starterId}"
            registerWorker(taskType + "-" + config.getStarterId(), false);

            // worker marking completion of process instance via "task-type-{starterId}-complete"
            registerWorker(taskType + "-" + config.getStarterId() + "-completed", true);
        }
    }


    public class SimpleDelayCompletionHandler implements JobHandler {

        private boolean markProcessInstanceCompleted;

        public SimpleDelayCompletionHandler(boolean markProcessInstanceCompleted) {
            this.markProcessInstanceCompleted = markProcessInstanceCompleted;
        }

        @Override
        public void handle(JobClient jobClient, ActivatedJob job) throws Exception {
            var jobStartTime = Instant.now().toEpochMilli();
            // Auto-complete logic from https://github.com/camunda-community-hub/spring-zeebe/blob/ec41c5af1f64e512c8e7a8deea2aeacb35e61a16/client/spring-zeebe/src/main/java/io/camunda/zeebe/spring/client/jobhandling/JobHandlerInvokingSpringBeans.java#L24
            CompleteJobCommandStep1 completeCommand = jobClient.newCompleteCommand(job.getKey());
            CommandWrapper command = new RefactoredCommandWrapper(
                    (FinalCommandStep) completeCommand,
                    job.getDeadline(),
                    job.toString(),
                    exceptionHandlingStrategy,
                    micrometerMetricsRecorder);
            Map<String, Object> variables = job.getVariablesAsMap();
            Long delay = config.getTaskCompletionDelay();
            if (variables.containsKey("delay")) {
                delay = 0L + (Integer) variables.get("delay");
                LOG.info("Worker " + job.getType() +" will complete in " +delay+ " MS");
                
            }
            // schedule the completion asynchronously with the configured delay
            scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        var jobType =job.getType();
                        command.executeAsyncWithMetrics("job_completion",jobType,"complete");

                        var completionTime = Instant.now().toEpochMilli();
                        stats.incCompletedJobs();
                        stats.recordJobTypeCompletion(jobType,  completionTime-jobStartTime);

                        if (markProcessInstanceCompleted) {
                            Object startEpochMillis = job.getVariablesAsMap().get(StartPiExecutor.BENCHMARK_START_DATE_MILLIS);
                            if (startEpochMillis!=null && startEpochMillis instanceof Long) {
                                stats.incCompletedProcessInstances((Long)startEpochMillis, completionTime);
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
                                exceptionHandlingStrategy,
                                micrometerMetricsRecorder);
                        command.executeAsyncWithMetrics("job_error",job.getType(),bpmnError.getErrorCode()+"-"+bpmnError.getErrorMessage());
                    }
                }
            }, Instant.now().plusMillis(delay));
        }
    }

    private FinalCommandStep<Void> createThrowErrorCommand(JobClient jobClient, ActivatedJob job, ZeebeBpmnError bpmnError) {
        FinalCommandStep<Void> command = jobClient.newThrowErrorCommand(job.getKey()) // TODO: PR for taking a job only in command chain
                .errorCode(bpmnError.getErrorCode())
                .errorMessage(bpmnError.getErrorMessage());
        return command;
    }
}
