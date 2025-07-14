package org.camunda.community.benchmarks.utils;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BpmnJobTypeParserTest {

    @Test
    void shouldExtractJobTypesFromStaticTypeBpmn() throws Exception {
        Resource[] resources = {new ClassPathResource("bpmn/typical_process_10_jobtypes.bpmn")};
        String starterId = "testStarter";
        
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources, starterId);
        
        assertNotNull(jobTypes);
        assertEquals(10, jobTypes.size());
        assertTrue(jobTypes.contains("benchmark-task-1"));
        assertTrue(jobTypes.contains("benchmark-task-2"));
        assertTrue(jobTypes.contains("benchmark-task-3"));
        assertTrue(jobTypes.contains("benchmark-task-4"));
        assertTrue(jobTypes.contains("benchmark-task-5"));
        assertTrue(jobTypes.contains("benchmark-task-6"));
        assertTrue(jobTypes.contains("benchmark-task-7"));
        assertTrue(jobTypes.contains("benchmark-task-8"));
        assertTrue(jobTypes.contains("benchmark-task-9"));
        assertTrue(jobTypes.contains("benchmark-task-10"));
    }

    @Test
    void shouldIgnoreDynamicExpressionsInBpmn() throws Exception {
        Resource[] resources = {new ClassPathResource("bpmn/typical_process.bpmn")};
        String starterId = "testStarter";
        
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources, starterId);
        
        assertNotNull(jobTypes);
        assertEquals(0, jobTypes.size()); // Should ignore all dynamic expressions
    }

    @Test
    void shouldHandleEmptyResources() throws Exception {
        Resource[] resources = {};
        String starterId = "testStarter";
        
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources, starterId);
        
        assertNotNull(jobTypes);
        assertTrue(jobTypes.isEmpty());
    }

    @Test
    void shouldHandleNullResources() throws Exception {
        Resource[] resources = null;
        String starterId = "testStarter";
        
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources, starterId);
        
        assertNotNull(jobTypes);
        assertTrue(jobTypes.isEmpty());
    }
}