package org.camunda.community.benchmarks.utils;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BpmnJobTypeParserTest {

    @Test
    void shouldExtractStaticJobTypes() throws Exception {
        // Test extraction from BPMN files with static job types (not FEEL expressions)
        Resource[] resources = {new ClassPathResource("bpmn/typical_process_10_jobtypes.bpmn")};
        
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources);
        
        assertNotNull(jobTypes);
        assertEquals(10, jobTypes.size());
        assertTrue(jobTypes.contains("benchmark-task-1"));
        assertTrue(jobTypes.contains("benchmark-task-10"));
    }

    @Test
    void shouldIgnoreFEELExpressions() throws Exception {
        // Test that FEEL expressions are still ignored - use a file that has actual FEEL expressions
        Resource[] resources = {new ClassPathResource("test_job_type_prediction.bpmn")};
        
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources);
        
        assertNotNull(jobTypes);
        // This test BPMN file has mixed content: static job types and FEEL expressions
        // Only static job types should be extracted
        assertEquals(3, jobTypes.size()); // static-task-1, static-task-2, benchmark-task-task4
        assertTrue(jobTypes.contains("static-task-1"));
        assertTrue(jobTypes.contains("static-task-2"));
    }

    @Test
    void shouldIgnoreDynamicExpressionsInBpmn() throws Exception {
        Resource[] resources = {new ClassPathResource("bpmn/typical_process.bpmn")};
        
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources);
        
        assertNotNull(jobTypes);
        assertEquals(0, jobTypes.size()); // Should ignore all FEEL expressions that start with =
    }

    @Test
    void shouldHandleEmptyResources() throws Exception {
        Resource[] resources = {};
        
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources);
        
        assertNotNull(jobTypes);
        assertTrue(jobTypes.isEmpty());
    }

    @Test
    void shouldHandleNullResources() throws Exception {
        Resource[] resources = null;
        
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources);
        
        assertNotNull(jobTypes);
        assertTrue(jobTypes.isEmpty());
    }

    @Test
    void shouldPredictJobTypesForServiceTasksWithoutJobTypes() throws Exception {
        // Load test BPMN file from classpath
        Resource[] resources = {new org.springframework.core.io.ClassPathResource("test_job_type_prediction.bpmn")};
        
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources);
        
        assertNotNull(jobTypes);
        assertEquals(3, jobTypes.size());
        
        // Should predict job types for tasks without zeebe:taskDefinition
        assertTrue(jobTypes.contains("benchmark-task-task4"));
        
        // Should extract explicit job types
        assertTrue(jobTypes.contains("static-task-1"));
        assertTrue(jobTypes.contains("static-task-2"));
    }
}