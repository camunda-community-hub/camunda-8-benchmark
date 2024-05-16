package org.camunda.community.benchmarks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

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

        // Replace job types or BPMN id on-the-fly

        byte[] stringBytes = is.readAllBytes();
        String fileContent = new String(stringBytes);

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

        return new ByteArrayInputStream(fileContent.getBytes());
    }
}
