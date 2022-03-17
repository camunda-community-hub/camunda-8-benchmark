package org.camunda.community.benchmarks;

import io.camunda.zeebe.client.ZeebeClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.camunda.community.benchmarks.config.AsyncConfiguration;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;

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
                LOG.info("Deploy " + config.getBpmnResource() + " to Zeebe now.");
                zeebeClient.newDeployCommand()
                        .addResourceStream(config.getBpmnResource().getInputStream(), config.getBpmnResource().getFilename())
                        .send();
            } catch (Exception ex) {
                throw new RuntimeException("Could not read process model for deployment: " + ex.getMessage(), ex);
            }
        }
    }
}
