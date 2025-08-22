package org.camunda.community.benchmarks;

import org.camunda.community.benchmarks.partition.PartitionHashUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify partition pinning logic integration
 */
public class PartitionPinningIntegrationTest {
    
    @Test
    void testClientIdParsing() {
        // Test various client name formats
        String[] clientNames = {
            "benchmark-0",
            "benchmark-3", 
            "my-benchmark-app-5",
            "statefulset-42",
            "invalid-name",
            "7"
        };
        
        for (String clientName : clientNames) {
            int extracted = PartitionHashUtil.extractClientIdFromName(clientName);
            assertTrue(extracted >= 0, "Extracted client ID should be non-negative for " + clientName);
        }
        
        // Test specific expected values
        assertEquals(0, PartitionHashUtil.extractClientIdFromName("benchmark-0"));
        assertEquals(3, PartitionHashUtil.extractClientIdFromName("benchmark-3"));
        assertEquals(5, PartitionHashUtil.extractClientIdFromName("my-benchmark-app-5"));
        assertEquals(42, PartitionHashUtil.extractClientIdFromName("statefulset-42"));
        assertEquals(0, PartitionHashUtil.extractClientIdFromName("invalid-name"));
        assertEquals(7, PartitionHashUtil.extractClientIdFromName("7"));
    }
    
    @Test
    void testJobTypeGeneration() {
        // Simulate the logic from ProcessDeployer.generateJobTypeForTask
        String taskId = "MyTask";
        String baseJobType = "benchmark-task-" + taskId;
        
        // Without partition pinning
        String normalJobType = baseJobType;
        assertEquals("benchmark-task-MyTask", normalJobType);
        
        // With partition pinning - numeric starter ID
        String starterId1 = "5";
        String partitionedJobType1 = baseJobType + "-" + starterId1;
        assertEquals("benchmark-task-MyTask-5", partitionedJobType1);
        
        // With partition pinning - starter name format
        String starterId2 = "benchmark-3";
        int extracted = PartitionHashUtil.extractClientIdFromName(starterId2);
        String partitionedJobType2 = baseJobType + "-" + extracted;
        assertEquals("benchmark-task-MyTask-3", partitionedJobType2);
    }
}