package org.camunda.community.benchmarks.utils;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for parsing job types from BPMN XML files using Zeebe BPMN Model API.
 */
public class BpmnJobTypeParser {

    private static final Logger LOG = LogManager.getLogger(BpmnJobTypeParser.class);

    /**
     * Extracts all unique job types from the given BPMN resources.
     * 
     * @param bpmnResources array of BPMN resource files to parse
     * @param starterId the starter ID to use for resolving dynamic expressions
     * @return set of unique job types found in the BPMN files
     * @throws IOException if there's an error reading the BPMN files
     */
    public static Set<String> extractJobTypes(Resource[] bpmnResources, String starterId) throws IOException {
        Set<String> jobTypes = new HashSet<>();
        
        if (bpmnResources == null || bpmnResources.length == 0) {
            LOG.warn("No BPMN resources provided for job type extraction");
            return jobTypes;
        }

        for (Resource resource : bpmnResources) {
            if (resource != null && resource.exists()) {
                try {
                    Set<String> resourceJobTypes = extractJobTypesFromResource(resource, starterId);
                    jobTypes.addAll(resourceJobTypes);
                    LOG.info("Extracted {} job types from {}: {}", 
                        resourceJobTypes.size(), resource.getFilename(), resourceJobTypes);
                } catch (Exception e) {
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
     */
    private static Set<String> extractJobTypesFromResource(Resource resource, String starterId) throws IOException {
        Set<String> jobTypes = new HashSet<>();
        
        try (InputStream inputStream = resource.getInputStream()) {
            BpmnModelInstance modelInstance = Bpmn.readModelFromStream(inputStream);
            Collection<ServiceTask> serviceTasks = modelInstance.getModelElementsByType(ServiceTask.class);
            
            for (ServiceTask serviceTask : serviceTasks) {
                Collection<ZeebeTaskDefinition> taskDefinitions = serviceTask.getExtensionElements()
                    .getElementsQuery().filterByType(ZeebeTaskDefinition.class).list();
                
                for (ZeebeTaskDefinition taskDef : taskDefinitions) {
                    String type = taskDef.getType();
                    if (type != null && !type.trim().isEmpty() && !type.startsWith("=")) {
                        jobTypes.add(type.trim());
                    }
                }
            }
        }
        
        return jobTypes;
    }
}