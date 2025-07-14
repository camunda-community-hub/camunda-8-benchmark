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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                    if (type != null && !type.trim().isEmpty()) {
                        String resolvedType = resolveJobType(type, starterId);
                        if (resolvedType != null) {
                            jobTypes.add(resolvedType);
                        }
                    }
                }
            }
        }
        
        return jobTypes;
    }

    /**
     * Resolves job type expressions to concrete job type strings.
     * Handles both static types and dynamic expressions.
     */
    private static String resolveJobType(String jobTypeExpression, String starterId) {
        if (jobTypeExpression == null || jobTypeExpression.trim().isEmpty()) {
            return null;
        }

        // Remove leading/trailing whitespace
        jobTypeExpression = jobTypeExpression.trim();
        
        // If it's a simple string literal (quoted), extract the content
        if (jobTypeExpression.startsWith("\"") && jobTypeExpression.endsWith("\"") && 
            !jobTypeExpression.contains("+")) {
            return jobTypeExpression.substring(1, jobTypeExpression.length() - 1);
        }
        
        // Handle expressions like = "benchmark-task-" + benchmark_starter_id
        if (jobTypeExpression.startsWith("=")) {
            return resolveExpression(jobTypeExpression.substring(1).trim(), starterId);
        }
        
        // If it's not an expression, treat as literal
        return jobTypeExpression;
    }

    /**
     * Resolves FEEL expressions to concrete job type strings.
     */
    private static String resolveExpression(String expression, String starterId) {
        // Handle expressions like "benchmark-task-" + benchmark_starter_id + "-completed"
        Pattern complexPattern = Pattern.compile("\"([^\"]+)\"\\s*\\+\\s*benchmark_starter_id\\s*\\+\\s*\"([^\"]+)\"");
        Matcher complexMatcher = complexPattern.matcher(expression);
        
        if (complexMatcher.find()) {
            String prefix = complexMatcher.group(1);
            String suffix = complexMatcher.group(2);
            return prefix + starterId + suffix;
        }
        
        // Handle expressions like "benchmark-task-" + benchmark_starter_id
        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*\\+\\s*benchmark_starter_id");
        Matcher matcher = pattern.matcher(expression);
        
        if (matcher.find()) {
            String prefix = matcher.group(1);
            return prefix + starterId;
        }
        
        // Handle simple quoted strings
        pattern = Pattern.compile("\"([^\"]+)\"");
        matcher = pattern.matcher(expression);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // If we can't parse the expression, log a warning and return null
        LOG.warn("Could not resolve job type expression: {}", expression);
        return null;
    }
}