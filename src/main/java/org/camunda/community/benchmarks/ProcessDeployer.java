package org.camunda.community.benchmarks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Definitions;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.UUID;

@Component
public class ProcessDeployer {

    private static final Logger LOG = LogManager.getLogger(ProcessDeployer.class);

    @Autowired
    private ZeebeClient zeebeClient;

    @Autowired
    private BenchmarkConfiguration config;

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

    private InputStream adjustInputStreamBasedOnConfig(InputStream is) throws IOException {
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
    private String injectUniqueJobTypes(String bpmnContent) throws Exception {
        // First, check if zeebe namespace needs to be added
        String modifiedContent = bpmnContent;
        if (!bpmnContent.contains("xmlns:zeebe=")) {
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
                // Generate a unique job type based on the task ID
                String taskId = serviceTask.getId();
                String uniqueJobType = "benchmark-task-" + (taskId != null && !taskId.isEmpty() ? taskId : UUID.randomUUID().toString());
                
                // Get or create extensionElements
                ExtensionElements extensionElements = serviceTask.getExtensionElements();
                if (extensionElements == null) {
                    extensionElements = modelInstance.newInstance(ExtensionElements.class);
                    serviceTask.setExtensionElements(extensionElements);
                }
                
                // Create zeebe:taskDefinition
                ZeebeTaskDefinition taskDefinition = modelInstance.newInstance(ZeebeTaskDefinition.class);
                taskDefinition.setType(uniqueJobType);
                extensionElements.addChildElement(taskDefinition);
                
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
