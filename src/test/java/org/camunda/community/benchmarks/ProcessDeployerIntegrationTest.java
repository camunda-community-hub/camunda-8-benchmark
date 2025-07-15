package org.camunda.community.benchmarks;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProcessDeployerIntegrationTest {

    private ProcessDeployer processDeployer;
    private BenchmarkConfiguration config;

    @BeforeEach
    void setUp() {
        config = new BenchmarkConfiguration();
        processDeployer = new ProcessDeployer(null, config); // zeebeClient not needed for testing
    }

    @Test
    void testSequence50TasksBpmn() throws Exception {
        String testFilePath = "Sequence_50_Tasks.bpmn";
        
        // Check if the file exists
        if (!Files.exists(Paths.get(testFilePath))) {
            System.out.println("Test file not found: " + testFilePath + ". Skipping integration test.");
            return;
        }
        
        // Read the original BPMN file
        try (InputStream inputStream = new FileInputStream(testFilePath)) {
            // Call the package-private method directly
            InputStream resultStream = processDeployer.adjustInputStreamBasedOnConfig(inputStream);
            
            String result = new String(resultStream.readAllBytes());
            
            // Verify zeebe namespace was added
            assertTrue(result.contains("xmlns:zeebe=\"http://camunda.org/schema/zeebe/1.0\""), 
                      "Zeebe namespace should be added");
            
            // Verify that all service tasks now have job types
            // Count service tasks in the result
            long serviceTaskCount = result.lines()
                .filter(line -> line.contains("<bpmn:serviceTask"))
                .count();
            
            // Count zeebe:taskDefinition entries
            long taskDefinitionCount = result.lines()
                .filter(line -> line.contains("<zeebe:taskDefinition"))
                .count();
            
            System.out.println("Found " + serviceTaskCount + " service tasks");
            System.out.println("Found " + taskDefinitionCount + " task definitions");
            
            // Each service task should have a task definition
            assertTrue(taskDefinitionCount >= serviceTaskCount, 
                      "Each service task should have a zeebe:taskDefinition");
            
            // Verify specific task IDs get the expected job types
            assertTrue(result.contains("type=\"benchmark-task-Task_1\""), 
                      "Task_1 should have job type benchmark-task-Task_1");
            assertTrue(result.contains("type=\"benchmark-task-Task_2\""), 
                      "Task_2 should have job type benchmark-task-Task_2");
            
            // Print a sample of the modified content for verification
            System.out.println("\nSample of modified BPMN (first service task):");
            String[] lines = result.split("\n");
            boolean inServiceTask = false;
            int lineCount = 0;
            for (String line : lines) {
                if (line.contains("<bpmn:serviceTask")) {
                    inServiceTask = true;
                }
                if (inServiceTask) {
                    System.out.println(line);
                    lineCount++;
                    if (line.contains("</bpmn:serviceTask>") || lineCount > 10) {
                        break;
                    }
                }
            }
        }
    }
}