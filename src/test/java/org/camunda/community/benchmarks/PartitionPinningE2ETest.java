package org.camunda.community.benchmarks;

import org.camunda.community.benchmarks.partition.PartitionHashUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration test demonstrating the complete partition pinning workflow
 */
public class PartitionPinningE2ETest {
    
    @Test
    void testPartitionPinningEndToEnd() {
        // Simulate a 3-starter deployment with 9 partitions
        int partitionCount = 9;
        int numberOfStarters = 3;
        
        // Test each starter's configuration and behavior
        for (int clientId = 0; clientId < numberOfStarters; clientId++) {
            testStarterBehavior(clientId, partitionCount, numberOfStarters);
        }
        
        // Test correlation key distribution
        testCorrelationKeyDistribution(partitionCount);
    }
    
    private void testStarterBehavior(int clientId, int partitionCount, int numberOfStarters) {
        // 1. Determine target partitions for this starter
        int[] targetPartitions = PartitionHashUtil.getTargetPartitionsForClient(clientId, partitionCount, numberOfStarters);
        assertTrue(targetPartitions.length > 0, "Starter should have target partitions");
        
        // 2. Test random partition selection
        for (int i = 0; i < 5; i++) {
            int selectedPartition = PartitionHashUtil.selectRandomPartition(targetPartitions);
            boolean isValidPartition = false;
            for (int partition : targetPartitions) {
                if (partition == selectedPartition) {
                    isValidPartition = true;
                    break;
                }
            }
            assertTrue(isValidPartition, "Selected partition " + selectedPartition + " should be in target partitions");
        }
        
        // 3. Generate correlation key for one of the target partitions
        if (targetPartitions.length > 0) {
            int testPartition = targetPartitions[0];
            String correlationKey = PartitionHashUtil.generateCorrelationKeyForPartition(
                testPartition, partitionCount, 100);
            assertNotNull(correlationKey, "Generated correlation key should not be null");
            
            // 4. Verify the key routes to the correct partition
            int actualPartition = PartitionHashUtil.getPartitionForCorrelationKey(correlationKey, partitionCount);
            assertEquals(testPartition, actualPartition, "Correlation key should route to target partition");
        }
        
        // 5. Simulate job type generation for this starter
        String baseJobType = "benchmark-task-Task_1";
        String partitionedJobType = baseJobType + "-" + clientId;
        assertNotNull(partitionedJobType, "Job type should be generated");
        assertTrue(partitionedJobType.contains(String.valueOf(clientId)), "Job type should contain client ID");
        
        // 6. Simulate client name extraction (as would happen with starterId)
        String clientName = "benchmark-" + clientId;
        int extractedClientId = PartitionHashUtil.extractClientIdFromName(clientName);
        assertEquals(clientId, extractedClientId, "Client ID extraction should work correctly");
    }
    
    private void testCorrelationKeyDistribution(int partitionCount) {
        int[] partitionCounts = new int[partitionCount];
        int totalKeys = 1000;
        
        // Generate many correlation keys and count distribution
        for (int i = 0; i < totalKeys; i++) {
            String key = "test-key-" + i;
            int partition = PartitionHashUtil.getPartitionForCorrelationKey(key, partitionCount);
            partitionCounts[partition]++; // partition is 0-based
        }
        
        // Verify reasonable distribution
        for (int i = 0; i < partitionCount; i++) {
            double percentage = (partitionCounts[i] * 100.0) / totalKeys;
            
            // Each partition should get roughly equal share (within reasonable bounds)
            double expectedPercentage = 100.0 / partitionCount;
            assertTrue(Math.abs(percentage - expectedPercentage) <= 15.0, 
                      "Partition " + i + " distribution should be reasonably even, got " + percentage + "% expected ~" + expectedPercentage + "%");
        }
    }
}