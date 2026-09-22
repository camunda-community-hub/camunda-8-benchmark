package org.camunda.community.benchmarks.utils;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.BusinessRuleTask;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.IntermediateThrowEvent;
import io.camunda.zeebe.model.bpmn.instance.ScriptTask;
import io.camunda.zeebe.model.bpmn.instance.SendTask;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.Task;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledDecision;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeScript;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class for parsing job types from BPMN XML files using Zeebe BPMN Model API.
 * Covers all BPMN element types that can carry a job worker in Camunda 8:
 * Service Tasks, Send Tasks, Business Rule Tasks (job-worker implementation only),
 * Script Tasks (job-worker implementation only), Ad-hoc Sub-processes,
 * Intermediate Throw Events with a task definition, Task Listeners, and Execution Listeners.
 */
public class BpmnJobTypeParser {

    private static final Logger LOG = LogManager.getLogger(BpmnJobTypeParser.class);

    /**
     * Extracts all unique job types from the given BPMN resources.
     * Covers Service Tasks, Send Tasks, Business Rule Tasks (job-worker mode only),
     * Script Tasks (job-worker mode only), Ad-hoc Sub-processes,
     * Intermediate Throw Events with a task definition, Task Listeners, and Execution Listeners.
     * For tasks without an explicit job type, the job type that ProcessDeployer would
     * auto-inject is predicted and included.
     *
     * @param bpmnResources array of BPMN resource files to parse
     * @return set of unique job types found in the BPMN files
     */
    public static Set<String> extractJobTypes(Resource[] bpmnResources) {
        Set<String> jobTypes = new HashSet<>();
        
        if (bpmnResources == null || bpmnResources.length == 0) {
            LOG.warn("No BPMN resources provided for job type extraction");
            return jobTypes;
        }

        for (Resource resource : bpmnResources) {
            if (resource != null && resource.exists()) {
                try {
                    Set<String> resourceJobTypes = extractJobTypesFromResource(resource);
                    jobTypes.addAll(resourceJobTypes);
                    LOG.info("Extracted {} job types from {}: {}", 
                        resourceJobTypes.size(), resource.getFilename(), resourceJobTypes);
                } catch (IOException e) {
                    LOG.error("Failed to extract job types from resource {}: {}", 
                        resource.getFilename(), e.getMessage(), e);
                }
            }
        }

        LOG.info("Total unique job types extracted: {}", jobTypes);
        return jobTypes;
    }

    /**
     * Extracts job types from a single BPMN resource.
     * Covers all element types that can carry a job worker in Camunda 8.
     */
    private static Set<String> extractJobTypesFromResource(Resource resource) throws IOException {
        Set<String> jobTypes = new HashSet<>();
        
        try (InputStream inputStream = resource.getInputStream()) {
            BpmnModelInstance modelInstance = Bpmn.readModelFromStream(inputStream);

            // Service tasks and send tasks: always use a job worker
            for (Class<? extends Task> taskType : List.of(ServiceTask.class, SendTask.class)) {
                for (Task task : modelInstance.getModelElementsByType(taskType)) {
                    extractJobTypesFromTaskDefinition(task, jobTypes, false);
                }
            }

            // Business rule tasks: job-worker mode only (skip if backed by a DMN decision)
            for (BusinessRuleTask brt : modelInstance.getModelElementsByType(BusinessRuleTask.class)) {
                if (!hasDmnDecision(brt)) {
                    extractJobTypesFromTaskDefinition(brt, jobTypes, false);
                }
            }

            // Script tasks: job-worker mode only (skip if a FEEL script expression is present)
            for (ScriptTask st : modelInstance.getModelElementsByType(ScriptTask.class)) {
                if (!hasZeebeScript(st)) {
                    extractJobTypesFromTaskDefinition(st, jobTypes, false);
                }
            }

            // Ad-hoc sub-processes can be controlled by a job worker via ZeebeTaskDefinition
            for (AdHocSubProcess adHoc : modelInstance.getModelElementsByType(AdHocSubProcess.class)) {
                ExtensionElements ext = adHoc.getExtensionElements();
                if (ext != null) {
                    for (ZeebeTaskDefinition taskDef : ext.getElementsQuery().filterByType(ZeebeTaskDefinition.class).list()) {
                        String type = taskDef.getType();
                        if (type != null && !type.trim().isEmpty() && !type.startsWith("=")) {
                            jobTypes.add(type.trim());
                        }
                    }
                }
            }

            // Intermediate throw events (e.g. message throw events) with an explicit task definition
            for (IntermediateThrowEvent throwEvent : modelInstance.getModelElementsByType(IntermediateThrowEvent.class)) {
                ExtensionElements ext = throwEvent.getExtensionElements();
                if (ext != null) {
                    for (ZeebeTaskDefinition taskDef : ext.getElementsQuery().filterByType(ZeebeTaskDefinition.class).list()) {
                        String type = taskDef.getType();
                        if (type != null && !type.trim().isEmpty() && !type.startsWith("=")) {
                            jobTypes.add(type.trim());
                        }
                    }
                }
            }

            // Task listeners (attached to any element)
            for (ZeebeTaskListener listener : modelInstance.getModelElementsByType(ZeebeTaskListener.class)) {
                String type = listener.getType();
                if (type != null && !type.trim().isEmpty() && !type.startsWith("=")) {
                    jobTypes.add(type.trim());
                }
            }

            // Execution listeners (attached to any element)
            for (ZeebeExecutionListener listener : modelInstance.getModelElementsByType(ZeebeExecutionListener.class)) {
                String type = listener.getType();
                if (type != null && !type.trim().isEmpty() && !type.startsWith("=")) {
                    jobTypes.add(type.trim());
                }
            }
        }
        
        return jobTypes;
    }

    /** Returns true if this business rule task uses a DMN decision (not a job worker). */
    private static boolean hasDmnDecision(Task task) {
        ExtensionElements ext = task.getExtensionElements();
        if (ext == null) {
            return false;
        }
        return !ext.getElementsQuery().filterByType(ZeebeCalledDecision.class).list().isEmpty();
    }

    /** Returns true if this script task uses a FEEL expression (not a job worker). */
    private static boolean hasZeebeScript(Task task) {
        ExtensionElements ext = task.getExtensionElements();
        if (ext == null) {
            return false;
        }
        return !ext.getElementsQuery().filterByType(ZeebeScript.class).list().isEmpty();
    }

    /**
     * Extracts job types from a task element's ZeebeTaskDefinition, or predicts a job type
     * if no task definition is present (matching the auto-injection logic in ProcessDeployer).
     *
     * @param skipPrediction when true, no predicted job type is added for tasks without a definition
     */
    private static void extractJobTypesFromTaskDefinition(Task task, Set<String> jobTypes, boolean skipPrediction) {
        boolean hasTaskDefinition = false;
        if (task.getExtensionElements() != null) {
            Collection<ZeebeTaskDefinition> taskDefinitions = task.getExtensionElements()
                .getElementsQuery().filterByType(ZeebeTaskDefinition.class).list();
            if (!taskDefinitions.isEmpty()) {
                hasTaskDefinition = true;
                for (ZeebeTaskDefinition taskDef : taskDefinitions) {
                    String type = taskDef.getType();
                    if (type != null && !type.trim().isEmpty() && !type.startsWith("=")) {
                        jobTypes.add(type.trim());
                    }
                }
            }
        }
        if (!hasTaskDefinition && !skipPrediction) {
            String taskId = task.getId();
            if (taskId != null && !taskId.trim().isEmpty()) {
                String predictedJobType = "benchmark-task-" + taskId.trim();
                jobTypes.add(predictedJobType);
                LOG.debug("Predicted job type '{}' for task '{}' without zeebe:taskDefinition",
                    predictedJobType, taskId);
            }
        }
    }
}