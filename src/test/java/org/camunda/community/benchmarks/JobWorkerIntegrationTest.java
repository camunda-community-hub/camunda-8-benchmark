package org.camunda.community.benchmarks;

import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.camunda.community.benchmarks.utils.BpmnJobTypeParser;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify JobWorker can extract job types from BPMN and use them
 * to register workers, falling back to configuration when needed.
 */
class JobWorkerIntegrationTest {

    @Test
    void shouldExtractJobTypesFromBpmnAndFallbackToConfig() throws Exception {
        // Test 1: Direct BPMN parsing - with current BPMN files using FEEL expressions
        Resource[] resources = {new ClassPathResource("bpmn/typical_process_10_jobtypes.bpmn")};
        Set<String> bpmnJobTypes = BpmnJobTypeParser.extractJobTypes(resources, "testStarter");
        
        // BPMN files use FEEL expressions starting with =, so they're ignored
        assertEquals(0, bpmnJobTypes.size());
        
        // Test 2: JobWorker fallback configuration method
        JobWorker jobWorker = new JobWorker();
        BenchmarkConfiguration config = new BenchmarkConfiguration();
        
        // Set private config field using reflection
        setPrivateField(jobWorker, "config", config);
        
        // Test fallback with single job type
        config.setJobType("fallback-task");
        config.setMultipleJobTypes(0);
        
        Set<String> configJobTypes = callPrivateMethod(jobWorker, "getJobTypesFromConfiguration");
        assertEquals(1, configJobTypes.size());
        assertTrue(configJobTypes.contains("fallback-task"));
        
        // Test fallback with multiple job types
        config.setMultipleJobTypes(3);
        configJobTypes = callPrivateMethod(jobWorker, "getJobTypesFromConfiguration");
        assertEquals(3, configJobTypes.size());
        assertTrue(configJobTypes.contains("fallback-task-1"));
        assertTrue(configJobTypes.contains("fallback-task-2"));
        assertTrue(configJobTypes.contains("fallback-task-3"));
        
        // Test fallback with comma-separated job types
        config.setJobType("type1,type2,type3");
        config.setMultipleJobTypes(0);
        configJobTypes = callPrivateMethod(jobWorker, "getJobTypesFromConfiguration");
        assertEquals(3, configJobTypes.size());
        assertTrue(configJobTypes.contains("type1"));
        assertTrue(configJobTypes.contains("type2"));
        assertTrue(configJobTypes.contains("type3"));
    }

    @Test
    void shouldIgnoreComplexBpmnExpressions() throws Exception {
        // Test with dynamic expressions - should be ignored (all FEEL expressions starting with =)
        Resource[] resources = {new ClassPathResource("bpmn/typical_process.bpmn")};
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources, "dynamicStarter");
        
        assertEquals(0, jobTypes.size()); // All FEEL expressions should be ignored
    }

    // Utility methods for reflection
    @SuppressWarnings("unchecked")
    private Set<String> callPrivateMethod(Object obj, String methodName) throws Exception {
        Method method = obj.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (Set<String>) method.invoke(obj);
    }

    private void setPrivateField(Object obj, String fieldName, Object value) throws Exception {
        var field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}