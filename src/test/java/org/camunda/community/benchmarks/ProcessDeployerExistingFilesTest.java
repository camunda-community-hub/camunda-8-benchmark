package org.camunda.community.benchmarks;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStream;

import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProcessDeployerExistingFilesTest {

    private ProcessDeployer processDeployer;
    private BenchmarkConfiguration config;

    @BeforeEach
    void setUp() {
        config = new BenchmarkConfiguration();
        processDeployer = new ProcessDeployer(null, config); // zeebeClient not needed for testing
    }

    @Test
    void testExistingBpmnFilesNotModified() throws Exception {
        // Test typical_process.bpmn which already has job types
        try (InputStream inputStream = getClass().getResourceAsStream("/bpmn/typical_process.bpmn")) {
            if (inputStream == null) {
                System.out.println("typical_process.bpmn not found, skipping test");
                return;
            }
            
            String originalContent = new String(inputStream.readAllBytes());
            
            // Reset stream for processing
            try (InputStream inputStream2 = getClass().getResourceAsStream("/bpmn/typical_process.bpmn")) {
                // Call the package-private method directly
                InputStream resultStream = processDeployer.adjustInputStreamBasedOnConfig(inputStream2);
                
                String result = new String(resultStream.readAllBytes());
                
                // Verify the original job type expressions are preserved
                assertTrue(result.contains("&#34;benchmark-task-&#34; + benchmark_starter_id"), 
                          "Original job type expressions should be preserved");
                
                // Verify no new benchmark-task-task1, benchmark-task-task2 etc. were added
                assertFalse(result.contains("type=\"benchmark-task-task1\""), 
                           "No new static job types should be added to existing tasks");
                assertFalse(result.contains("type=\"benchmark-task-task2\""), 
                           "No new static job types should be added to existing tasks");
                
                assertEquals(originalContent, result, "Existing BPMN file should be correctly preserved");
            }
        }
    }

    @Test
    void testTypicalProcess10JobTypesNotModified() throws Exception {
        // Test typical_process_10_jobtypes.bpmn which already has specific job types
        try (InputStream inputStream = getClass().getResourceAsStream("/bpmn/typical_process_10_jobtypes.bpmn")) {
            if (inputStream == null) {
                System.out.println("typical_process_10_jobtypes.bpmn not found, skipping test");
                return;
            }
            
            String originalContent = new String(inputStream.readAllBytes());
            
            // Reset stream for processing
            try (InputStream inputStream2 = getClass().getResourceAsStream("/bpmn/typical_process_10_jobtypes.bpmn")) {
                // Call the package-private method directly
                InputStream resultStream = processDeployer.adjustInputStreamBasedOnConfig(inputStream2);
                
                String result = new String(resultStream.readAllBytes());
                
                // Verify the original job types are preserved (now static, not FEEL expressions)
                assertTrue(result.contains("type=\"benchmark-task-1\""), 
                          "Original benchmark-task-1 should be preserved");
                assertTrue(result.contains("type=\"benchmark-task-2\""), 
                          "Original benchmark-task-2 should be preserved");
                
                // Verify no duplicate job types were added
                long task1Count = result.lines().filter(line -> line.contains("benchmark-task-1")).count();
                assertTrue(task1Count <= 2, "Should not have duplicate task-1 job types"); // One for task definition, one for comment
                
                assertEquals(originalContent, result, "Existing 10 job types BPMN file should be correctly preserved");
            }
        }
    }
}