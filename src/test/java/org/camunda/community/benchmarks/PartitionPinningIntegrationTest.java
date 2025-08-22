package org.camunda.community.benchmarks;

import org.camunda.community.benchmarks.partition.PartitionHashUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify partition pinning logic integration
 */
public class PartitionPinningIntegrationTest {
  
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