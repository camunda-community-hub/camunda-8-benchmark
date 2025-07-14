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
import java.io.StringWriter;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
     */
    private String injectUniqueJobTypes(String bpmnContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        Document doc = builder.parse(new ByteArrayInputStream(bpmnContent.getBytes()));
        
        boolean modified = false;
        
        // Get the root element (bpmn:definitions)
        Element root = doc.getDocumentElement();
        
        // Check if zeebe namespace is already declared
        String zeebeNamespace = root.getAttribute("xmlns:zeebe");
        if (zeebeNamespace == null || zeebeNamespace.isEmpty()) {
            root.setAttribute("xmlns:zeebe", "http://camunda.org/schema/zeebe/1.0");
            modified = true;
        }
        
        // Find all service tasks
        NodeList serviceTasks = doc.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "serviceTask");
        
        for (int i = 0; i < serviceTasks.getLength(); i++) {
            Element serviceTask = (Element) serviceTasks.item(i);
            
            // Check if this service task already has a zeebe:taskDefinition
            if (!hasZeebeTaskDefinition(serviceTask)) {
                // Generate a unique job type based on the task ID
                String taskId = serviceTask.getAttribute("id");
                String uniqueJobType = "benchmark-task-" + (taskId != null && !taskId.isEmpty() ? taskId : UUID.randomUUID().toString());
                
                // Create extensionElements if it doesn't exist
                Element extensionElements = getOrCreateExtensionElements(serviceTask);
                
                // Create zeebe:taskDefinition
                Element taskDefinition = doc.createElementNS("http://camunda.org/schema/zeebe/1.0", "zeebe:taskDefinition");
                taskDefinition.setAttribute("type", uniqueJobType);
                
                extensionElements.appendChild(taskDefinition);
                modified = true;
                
                LOG.info("Added job type '{}' to service task '{}'", uniqueJobType, taskId);
            }
        }
        
        if (modified) {
            // Convert document back to string
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        }
        
        return bpmnContent;
    }
    
    /**
     * Check if a service task already has a zeebe:taskDefinition
     */
    private boolean hasZeebeTaskDefinition(Element serviceTask) {
        NodeList extensionElements = serviceTask.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "extensionElements");
        if (extensionElements.getLength() == 0) {
            return false;
        }
        
        Element extensionElement = (Element) extensionElements.item(0);
        NodeList taskDefinitions = extensionElement.getElementsByTagNameNS("http://camunda.org/schema/zeebe/1.0", "taskDefinition");
        return taskDefinitions.getLength() > 0;
    }
    
    /**
     * Get or create bpmn:extensionElements for a service task
     */
    private Element getOrCreateExtensionElements(Element serviceTask) {
        NodeList extensionElements = serviceTask.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "extensionElements");
        
        if (extensionElements.getLength() > 0) {
            return (Element) extensionElements.item(0);
        }
        
        // Create new extensionElements
        Element extensionElement = serviceTask.getOwnerDocument().createElementNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "bpmn:extensionElements");
        
        // Insert as first child to maintain proper BPMN structure
        Node firstChild = serviceTask.getFirstChild();
        if (firstChild != null) {
            serviceTask.insertBefore(extensionElement, firstChild);
        } else {
            serviceTask.appendChild(extensionElement);
        }
        
        return extensionElement;
    }
}
