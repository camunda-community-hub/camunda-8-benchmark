package org.camunda.community.benchmarks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;

@Component
public class ProcessDeployer {

    private static final Logger LOG = LogManager.getLogger(ProcessDeployer.class);

    private final ZeebeClient zeebeClient;
    private final BenchmarkConfiguration config;

    public ProcessDeployer(ZeebeClient zeebeClient, BenchmarkConfiguration config) {
        this.zeebeClient = zeebeClient;
        this.config = config;
    }

    // Can't do @PostContruct, as this is called before the client is ready
    public void autoDeploy() {
        if (config.isAutoDeployProcess()) {
            try {
                LOG.info("Deploy " + StringUtils.arrayToCommaDelimitedString(config.getBpmnResource()) + " to Zeebe...");
                DeployResourceCommandStep1.DeployResourceCommandStep2 deployResourceCommand = zeebeClient.newDeployResourceCommand()
                        .addResourceStream(adjustInputStreamBasedOnConfig(config.getBpmnResource()[0].getInputStream()), config.getBpmnResource()[0].getFilename()); // Have to add at least the first resource to have the right class of Step2
                for (int i = 1; i < config.getBpmnResource().length; i++) { // now adding the rest of resources starting from 1
                    deployResourceCommand = deployResourceCommand.addResourceStream(adjustInputStreamBasedOnConfig(config.getBpmnResource()[i].getInputStream()), config.getBpmnResource()[i].getFilename());
                }
                deployResourceCommand.send().join();
            } catch (Exception ex) {
                throw new RuntimeException("Could not deploy to Zeebe: " + ex.getMessage(), ex);
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
        
        // Find all service tasks and inject job types where needed
        Collection<ServiceTask> serviceTasks = modelInstance.getModelElementsByType(ServiceTask.class);
        
        for (ServiceTask serviceTask : serviceTasks) {
            // Check if this service task already has a zeebe:taskDefinition
            if (!hasZeebeTaskDefinition(serviceTask)) {
                // Generate a unique job type based on the task ID and partition pinning config
                String taskId = serviceTask.getId();
                String uniqueJobType = generateJobTypeForTask(taskId);
                
                // Use ServiceTaskBuilder fluent API to add job type
                ServiceTaskBuilder builder = new ServiceTaskBuilder(modelInstance, serviceTask);
                builder.zeebeJobType(uniqueJobType);
                
                modified = true;
                LOG.info("Added job type '{}' to service task '{}'", uniqueJobType, taskId);
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
        // TODO: use config.getJobType() as prefix
        String baseJobType = "benchmark-task-" + taskId;
        
        // If partition pinning is enabled, append client ID to make job type unique per client
        if (config.isEnablePartitionPinning()) {
            String starterId = config.getStarterId();
            if (starterId != null && !starterId.isEmpty()) {
                String clientIdSuffix = starterId;
                // Extract numeric part if it's a starter name format
                try {
                    int numericClientId = Integer.parseInt(starterId);
                    clientIdSuffix = String.valueOf(numericClientId);
                } catch (NumberFormatException e) {
                    // If not numeric, extract from starter name or use as is
                    int extracted = org.camunda.community.benchmarks.partition.PartitionHashUtil.extractClientIdFromName(starterId);
                    clientIdSuffix = String.valueOf(extracted);
                }
                return baseJobType + "-" + clientIdSuffix;
            }
        }
        
        return baseJobType;
    }
    
    /**
     * Check if a service task already has a zeebe:taskDefinition using BPMN model API
     */
    private boolean hasZeebeTaskDefinition(ServiceTask serviceTask) {
        ExtensionElements extensionElements = serviceTask.getExtensionElements();
        if (extensionElements == null) {
            return false;
        }
        
        Collection<ZeebeTaskDefinition> taskDefinitions = extensionElements.getElementsQuery()
            .filterByType(ZeebeTaskDefinition.class)
            .list();
        
        return !taskDefinitions.isEmpty();
    }
}
