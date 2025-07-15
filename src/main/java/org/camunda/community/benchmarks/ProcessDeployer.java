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
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

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
        if (config.getJobTypesToReplace()==null && config.getBpmnProcessIdToReplace()==null) {
            return is;
        }

        // Read the input stream
        byte[] stringBytes = is.readAllBytes();

        if (config.getJobTypesToReplace()!=null) {
            // Use BPMN Model API to properly modify ServiceTask job types
            try {
                BpmnModelInstance modelInstance = Bpmn.readModelFromStream(new ByteArrayInputStream(stringBytes));
                
                // Find all ServiceTask elements
                Collection<ServiceTask> serviceTasks = modelInstance.getModelElementsByType(ServiceTask.class);
                
                for (ServiceTask serviceTask : serviceTasks) {
                    // Create ServiceTaskBuilder with existing task and model instance
                    ServiceTaskBuilder builder = new ServiceTaskBuilder(modelInstance, serviceTask);
                    
                    // Set the new job type using the fluent API
                    builder.zeebeJobType(config.getJobType());
                }
                
                // Convert the modified model back to string
                String modifiedBpmn = Bpmn.convertToString(modelInstance);
                stringBytes = modifiedBpmn.getBytes();
                
            } catch (Exception e) {
                LOG.warn("Failed to parse BPMN with model API, falling back to string replacement: " + e.getMessage());
                // Fallback to original string replacement if BPMN parsing fails
                String fileContent = new String(stringBytes);
                String[] tasksToReplace = {config.getJobTypesToReplace()};
                if (config.getJobTypesToReplace().contains(",")) {
                    tasksToReplace = config.getJobTypesToReplace().split(",");
                }
                for (String taskToReplace: tasksToReplace) {
                    fileContent = fileContent.replaceAll(taskToReplace, config.getJobType());
                }
                stringBytes = fileContent.getBytes();
            }
        }

        if (config.getBpmnProcessIdToReplace()!=null) {
            // Keep string replacement for process ID as it's simpler and reliable
            String fileContent = new String(stringBytes);
            fileContent = fileContent.replaceAll(config.getBpmnProcessIdToReplace(), config.getBpmnProcessId());
            stringBytes = fileContent.getBytes();
        }

        return new ByteArrayInputStream(stringBytes);
    }
}
