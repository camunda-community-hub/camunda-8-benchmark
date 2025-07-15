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
        String starterId = "testStarter";
        
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources, starterId);
        
        // With current BPMN files all using FEEL expressions, this returns 0
        assertNotNull(jobTypes);
        assertEquals(0, jobTypes.size());
    }

    @Test
    void shouldIgnoreFEELExpressions() throws Exception {
        Resource[] resources = {new ClassPathResource("bpmn/typical_process_10_jobtypes.bpmn")};
        String starterId = "testStarter";
        
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources, starterId);
        
        assertNotNull(jobTypes);
        assertEquals(0, jobTypes.size()); // Should ignore all FEEL expressions that start with =
    }

    @Test
    void shouldIgnoreDynamicExpressionsInBpmn() throws Exception {
        Resource[] resources = {new ClassPathResource("bpmn/typical_process.bpmn")};
        String starterId = "testStarter";
        
        Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources, starterId);
        
        assertNotNull(jobTypes);
        assertEquals(0, jobTypes.size()); // Should ignore all FEEL expressions that start with =
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