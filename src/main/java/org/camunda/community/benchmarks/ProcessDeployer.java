package org.camunda.community.benchmarks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import io.camunda.client.CamundaClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.BusinessRuleTaskBuilder;
import io.camunda.zeebe.model.bpmn.builder.ScriptTaskBuilder;
import io.camunda.zeebe.model.bpmn.builder.SendTaskBuilder;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.BusinessRuleTask;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.ScriptTask;
import io.camunda.zeebe.model.bpmn.instance.SendTask;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.Task;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledDecision;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeScript;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;

@Component
public class ProcessDeployer {

    private static final Logger LOG = LogManager.getLogger(ProcessDeployer.class);

    private final CamundaClient camundaClient;
    private final BenchmarkConfiguration config;

    public ProcessDeployer(CamundaClient camundaClient, BenchmarkConfiguration config) {
        this.camundaClient = camundaClient;
        this.config = config;
    }

    // Can't do @PostContruct, as this is called before the client is ready
    public void autoDeploy() {
        if (config.isAutoDeployProcess()) {
            try {
                LOG.info("Deploy " + StringUtils.arrayToCommaDelimitedString(config.getBpmnResource()) + " to Zeebe...");
                DeployResourceCommandStep1.DeployResourceCommandStep2 deployResourceCommand = camundaClient.newDeployResourceCommand()
                        .addResourceStream(adjustInputStreamBasedOnConfig(config.getBpmnResource()[0].getInputStream()), config.getBpmnResource()[0].getFilename()); // Have to add at least the first resource to have the right class of Step2
                for (int i = 1; i < config.getBpmnResource().length; i++) { // now adding the rest of resources starting from 1
                    deployResourceCommand = deployResourceCommand.addResourceStream(adjustInputStreamBasedOnConfig(config.getBpmnResource()[i].getInputStream()), config.getBpmnResource()[i].getFilename());
                }
                deployResourceCommand.send().join();
            } catch (Exception ex) {
                // shut down the application if deployment fails
                LOG.error("Could not deploy to Zeebe: " + ex.getMessage(), ex);
                System.exit(1);
            }
        }
    }

    InputStream adjustInputStreamBasedOnConfig(InputStream is) throws IOException {
        byte[] stringBytes = is.readAllBytes();
        String fileContent = new String(stringBytes);
        
        // First, inject job types for service tasks that don't have them
        try {
            fileContent = injectUniqueJobTypes(fileContent);
        } catch (Exception e) {
            LOG.warn("Failed to inject job types, proceeding with original content: " + e.getMessage());
        }
        
        // Then apply existing configuration-based replacements
        if (config.getJobTypesToReplace() != null || config.getBpmnProcessIdToReplace() != null) {
            if (config.getJobTypesToReplace()!=null) {
                // Split by "," if there are multiple task types to be replaced
                String[] tasksToReplace = {config.getJobTypesToReplace()};
                if (config.getJobTypesToReplace().contains(",")) {
                    tasksToReplace = config.getJobTypesToReplace().split(",");
                }
                for (String taskToReplace: tasksToReplace) {
                    fileContent = fileContent.replaceAll(taskToReplace, config.getJobType());
                }
            }
            if (config.getBpmnProcessIdToReplace()!=null) {
                fileContent = fileContent.replaceAll(config.getBpmnProcessIdToReplace(), config.getBpmnProcessId());
            }
        }

        return new ByteArrayInputStream(fileContent.getBytes());
    }

    /**
     * Inject unique job types for service tasks that don't have zeebe:taskDefinition
     * Uses Zeebe's BPMN model API for robust and type-safe BPMN manipulation
     */
    String injectUniqueJobTypes(String bpmnContent) throws Exception {
        // First, check if zeebe namespace needs to be added
        boolean hasZeebeNamespace = bpmnContent.contains("http://camunda.org/schema/zeebe/1.0");
        String modifiedContent = bpmnContent;
        
        if (!hasZeebeNamespace) {
            // Add zeebe namespace declaration using string replacement for safety
            modifiedContent = bpmnContent.replace(
                "<bpmn:definitions",
                "<bpmn:definitions xmlns:zeebe=\"http://camunda.org/schema/zeebe/1.0\""
            );
        }
        
        // Parse BPMN using Zeebe's BPMN model API
        BpmnModelInstance modelInstance = Bpmn.readModelFromStream(new ByteArrayInputStream(modifiedContent.getBytes()));
        
        boolean modified = false;
        
        // Service tasks: always use a job worker
        Collection<ServiceTask> serviceTasks = modelInstance.getModelElementsByType(ServiceTask.class);
        for (ServiceTask serviceTask : serviceTasks) {
            if (!hasZeebeTaskDefinition(serviceTask)) {
                String taskId = serviceTask.getId();
                String uniqueJobType = generateJobTypeForTask(taskId);
                new ServiceTaskBuilder(modelInstance, serviceTask).zeebeJobType(uniqueJobType);
                modified = true;
                LOG.info("Added job type '{}' to service task '{}'", uniqueJobType, taskId);
            }
        }

        // Send tasks: always use a job worker (same as service tasks)
        for (SendTask sendTask : modelInstance.getModelElementsByType(SendTask.class)) {
            if (!hasZeebeTaskDefinition(sendTask)) {
                String taskId = sendTask.getId();
                String uniqueJobType = generateJobTypeForTask(taskId);
                new SendTaskBuilder(modelInstance, sendTask).zeebeJobType(uniqueJobType);
                modified = true;
                LOG.info("Added job type '{}' to send task '{}'", uniqueJobType, taskId);
            }
        }

        // Business rule tasks: inject only when not backed by a DMN decision
        for (BusinessRuleTask brt : modelInstance.getModelElementsByType(BusinessRuleTask.class)) {
            if (!hasZeebeTaskDefinition(brt) && !hasDmnDecision(brt)) {
                String taskId = brt.getId();
                String uniqueJobType = generateJobTypeForTask(taskId);
                new BusinessRuleTaskBuilder(modelInstance, brt).zeebeJobType(uniqueJobType);
                modified = true;
                LOG.info("Added job type '{}' to business rule task '{}'", uniqueJobType, taskId);
            }
        }

        // Script tasks: inject only when not using a FEEL expression script
        for (ScriptTask st : modelInstance.getModelElementsByType(ScriptTask.class)) {
            if (!hasZeebeTaskDefinition(st) && !hasZeebeScript(st)) {
                String taskId = st.getId();
                String uniqueJobType = generateJobTypeForTask(taskId);
                new ScriptTaskBuilder(modelInstance, st).zeebeJobType(uniqueJobType);
                modified = true;
                LOG.info("Added job type '{}' to script task '{}'", uniqueJobType, taskId);
            }
        }
        
        if (modified || !bpmnContent.equals(modifiedContent)) {
            // Convert model back to string
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Bpmn.writeModelToStream(outputStream, modelInstance);
            return outputStream.toString();
        }
        
        return bpmnContent;
    }
    
    private String generateJobTypeForTask(String taskId) {
        // Note: taskId is guaranteed to be non-null and non-empty by BPMN specification
        String baseJobType = config.getJobType() + "-" + taskId;
        
        // If partition pinning is enabled, use full starter ID as prefix
        if (config.isEnablePartitionPinning()) {
            String starterId = config.getStarterId();
            if (starterId != null && !starterId.isEmpty()) {
                return "= " + StartPiExecutor.BENCHMARK_STARTER_ID + " + \"-" + baseJobType + "\"";
            }
        }
        
        return baseJobType;
    }
    
    /**
     * Check if a task already has a zeebe:taskDefinition using BPMN model API
     */
    private boolean hasZeebeTaskDefinition(Task task) {
        ExtensionElements extensionElements = task.getExtensionElements();
        if (extensionElements == null) {
            return false;
        }
        
        Collection<ZeebeTaskDefinition> taskDefinitions = extensionElements.getElementsQuery()
            .filterByType(ZeebeTaskDefinition.class)
            .list();
        
        return !taskDefinitions.isEmpty();
    }

    /** Returns true if this business rule task is backed by a DMN decision (not a job worker). */
    private boolean hasDmnDecision(Task task) {
        ExtensionElements ext = task.getExtensionElements();
        if (ext == null) {
            return false;
        }
        return !ext.getElementsQuery().filterByType(ZeebeCalledDecision.class).list().isEmpty();
    }

    /** Returns true if this script task uses a FEEL expression (not a job worker). */
    private boolean hasZeebeScript(Task task) {
        ExtensionElements ext = task.getExtensionElements();
        if (ext == null) {
            return false;
        }
        return !ext.getElementsQuery().filterByType(ZeebeScript.class).list().isEmpty();
    }
}
