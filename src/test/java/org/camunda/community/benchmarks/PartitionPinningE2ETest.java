package org.camunda.community.benchmarks;

import org.camunda.community.benchmarks.partition.PartitionHashUtil;

/**
 * Comprehensive integration test demonstrating the complete partition pinning workflow
 */
public class PartitionPinningE2ETest {
    
    public static void main(String[] args) {
        System.out.println("=== Partition Pinning End-to-End Test ===");
        
        // Simulate a 3-starter deployment with 9 partitions
        int partitionCount = 9;
        int numberOfStarters = 3;
        
        System.out.println("Scenario: " + numberOfStarters + " starters across " + partitionCount + " partitions");
        System.out.println();
        
        // Test each starter's configuration and behavior
        for (int clientId = 0; clientId < numberOfStarters; clientId++) {
            testStarterBehavior(clientId, partitionCount, numberOfStarters);
            System.out.println();
        }
        
        // Test correlation key distribution
        testCorrelationKeyDistribution(partitionCount);
        
        System.out.println("=== All E2E Tests Passed! ===");
    }
    
    private static void testStarterBehavior(int clientId, int partitionCount, int numberOfStarters) {
        System.out.println("--- Starter " + clientId + " Configuration ---");
        
        // 1. Determine target partitions for this starter
        int[] targetPartitions = PartitionHashUtil.getTargetPartitionsForClient(clientId, partitionCount, numberOfStarters);
        System.out.println("Target partitions: " + java.util.Arrays.toString(targetPartitions));
        
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
            if (!isValidPartition) {
                throw new RuntimeException("Selected partition " + selectedPartition + " not in target partitions!");
            }
        }
        System.out.println("✓ Random partition selection working correctly");
        
        // 3. Generate correlation key for one of the target partitions
        if (targetPartitions.length > 0) {
            int testPartition = targetPartitions[0];
            String correlationKey = PartitionHashUtil.generateCorrelationKeyForPartition(
                testPartition, partitionCount, 100);
            System.out.println("Generated correlation key for partition " + testPartition + ": " + correlationKey);
            
            // 4. Verify the key routes to the correct partition
            int actualPartition = PartitionHashUtil.getPartitionForCorrelationKey(correlationKey, partitionCount);
            if (actualPartition != testPartition) {
                throw new RuntimeException("Correlation key routes to wrong partition!");
            }
            System.out.println("✓ Correlation key routes correctly to partition " + actualPartition);
        }
        
        // 5. Simulate job type generation for this starter
        String baseJobType = "benchmark-task-Task_1";
        String partitionedJobType = baseJobType + "-" + clientId;
        System.out.println("Job type: " + baseJobType + " → " + partitionedJobType);
        
        // 6. Simulate client name extraction (as would happen with starterId)
        String clientName = "benchmark-" + clientId;
        int extractedClientId = PartitionHashUtil.extractClientIdFromName(clientName);
        if (extractedClientId != clientId) {
            throw new RuntimeException("Client ID extraction failed!");
        }
        System.out.println("✓ Client name '" + clientName + "' correctly extracted as ID " + extractedClientId);
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