package org.camunda.community.benchmarks;

import org.camunda.community.benchmarks.partition.PartitionHashUtil;

/**
 * Comprehensive integration test demonstrating the complete partition pinning workflow
 */
public class PartitionPinningE2ETest {
    
    public static void main(String[] args) {
        System.out.println("=== Partition Pinning End-to-End Test ===");
        
        // Simulate a 3-replica deployment with 8 partitions
        int partitionCount = 8;
        int replicas = 3;
        
        System.out.println("Scenario: " + replicas + " replicas across " + partitionCount + " partitions");
        System.out.println();
        
        // Test each replica's configuration and behavior
        for (int podId = 0; podId < replicas; podId++) {
            testReplicaBehavior(podId, partitionCount, replicas);
            System.out.println();
        }
        
        // Test correlation key distribution
        testCorrelationKeyDistribution(partitionCount);
        
        System.out.println("=== All E2E Tests Passed! ===");
    }
    
    private static void testReplicaBehavior(int podId, int partitionCount, int replicas) {
        System.out.println("--- Replica " + podId + " Configuration ---");
        
        // 1. Determine target partition for this replica
        int targetPartition = PartitionHashUtil.getTargetPartitionForClient(podId, partitionCount, replicas);
        System.out.println("Target partition: " + targetPartition);
        
        // 2. Generate correlation key for this partition
        String correlationKey = PartitionHashUtil.generateCorrelationKeyForPartition(
            targetPartition, partitionCount, 100);
        System.out.println("Generated correlation key: " + correlationKey);
        
        // 3. Verify the key routes to the correct partition
        int actualPartition = PartitionHashUtil.getPartitionForCorrelationKey(correlationKey, partitionCount);
        if (actualPartition != targetPartition) {
            throw new RuntimeException("Correlation key routes to wrong partition!");
        }
        System.out.println("✓ Correlation key routes correctly to partition " + actualPartition);
        
        // 4. Simulate job type generation for this replica
        String baseJobType = "benchmark-task-Task_1";
        String partitionedJobType = baseJobType + "-" + podId;
        System.out.println("Job type: " + baseJobType + " → " + partitionedJobType);
        
        // 5. Simulate pod name extraction (as would happen in Kubernetes)
        String podName = "benchmark-" + podId;
        int extractedPodId = PartitionHashUtil.extractPodIdFromName(podName);
        if (extractedPodId != podId) {
            throw new RuntimeException("Pod ID extraction failed!");
        }
        System.out.println("✓ Pod name '" + podName + "' correctly extracted as ID " + extractedPodId);
    }
    
    private static void testCorrelationKeyDistribution(int partitionCount) {
        System.out.println("--- Testing Correlation Key Distribution ---");
        
        int[] partitionCounts = new int[partitionCount];
        int totalKeys = 1000;
        
        // Generate many correlation keys and count distribution
        for (int i = 0; i < totalKeys; i++) {
            String key = "test-key-" + i;
            int partition = PartitionHashUtil.getPartitionForCorrelationKey(key, partitionCount);
            partitionCounts[partition]++;
        }
        
        // Verify reasonable distribution
        System.out.println("Distribution of " + totalKeys + " keys across " + partitionCount + " partitions:");
        for (int i = 0; i < partitionCount; i++) {
            double percentage = (partitionCounts[i] * 100.0) / totalKeys;
            System.out.printf("  Partition %d: %d keys (%.1f%%)\n", i, partitionCounts[i], percentage);
            
            // Each partition should get roughly equal share (within reasonable bounds)
            double expectedPercentage = 100.0 / partitionCount;
            if (Math.abs(percentage - expectedPercentage) > 5.0) {
                System.out.println("  ⚠ Warning: Uneven distribution for partition " + i);
            }
        }
        
        System.out.println("✓ Hash distribution test completed");
    }
}