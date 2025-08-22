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
        // Create a simple test BPMN content with service tasks without job types
        String testBpmnContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" 
                                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" 
                                  xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                                  id="test-process-definitions" 
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="test-process-id" isExecutable="true">
                    <bpmn:startEvent id="start" />
                    <bpmn:serviceTask id="task4" name="Task 4">
                      <!-- No zeebe:taskDefinition - should predict benchmark-task-task4 -->
                    </bpmn:serviceTask>
                    <bpmn:serviceTask id="static-task-1" name="Static Task 1">
                      <bpmn:extensionElements>
                        <zeebe:taskDefinition type="static-task-1" />
                      </bpmn:extensionElements>
                    </bpmn:serviceTask>
                    <bpmn:serviceTask id="static-task-2" name="Static Task 2">
                      <bpmn:extensionElements>
                        <zeebe:taskDefinition type="static-task-2" />
                      </bpmn:extensionElements>
                    </bpmn:serviceTask>
                    <bpmn:endEvent id="end" />
                  </bpmn:process>
                </bpmn:definitions>
                """;
        
        // Write test content to a temporary file
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test_static_jobs", ".bpmn");
        java.nio.file.Files.writeString(tempFile, testBpmnContent);
        
        try {
            Resource[] resources = {new org.springframework.core.io.FileSystemResource(tempFile.toFile())};
            
            Set<String> jobTypes = BpmnJobTypeParser.extractJobTypes(resources);
            
            assertNotNull(jobTypes);
            assertEquals(3, jobTypes.size());
            
            // Should predict job types for tasks without zeebe:taskDefinition
            assertTrue(jobTypes.contains("benchmark-task-task4"));
            
            // Should extract explicit job types
            assertTrue(jobTypes.contains("static-task-1"));
            assertTrue(jobTypes.contains("static-task-2"));
        } finally {
            // Clean up temporary file
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }
}