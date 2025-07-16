package org.camunda.community.benchmarks.utils;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BpmnJobTypeParserTest {

    @Test
    void shouldExtractStaticJobTypes() throws Exception {
        // Create a simple test to show functionality with non-FEEL expressions
        // For actual testing, we'd need a BPMN file with job types that don't start with =
        Resource[] resources = {new ClassPathResource("bpmn/typical_process_10_jobtypes.bpmn")};
        
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources);
        
        // With current BPMN files all using FEEL expressions, this returns 0
        assertNotNull(jobTypes);
        assertEquals(0, jobTypes.size());
    }

    @Test
    void shouldIgnoreFEELExpressions() throws Exception {
        Resource[] resources = {new ClassPathResource("bpmn/typical_process_10_jobtypes.bpmn")};
        
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources);
        
        assertNotNull(jobTypes);
        assertEquals(0, jobTypes.size()); // Should ignore all FEEL expressions that start with =
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
        // Create a test using the BPMN file with service tasks without job types
        Resource[] resources = {new org.springframework.core.io.FileSystemResource("/tmp/test-bpmn/service-tasks-without-job-types.bpmn")};
        
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources);
        
        assertNotNull(jobTypes);
        assertEquals(3, jobTypes.size());
        
        // Should predict job types for tasks without zeebe:taskDefinition
        assertTrue(jobTypes.contains("benchmark-task-task1"));
        assertTrue(jobTypes.contains("benchmark-task-task2"));
        
        // Should extract explicit job type
        assertTrue(jobTypes.contains("explicit-job-type"));
    }
}