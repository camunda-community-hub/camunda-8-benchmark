package org.camunda.community.benchmarks.partition;

/**
 * Simple test runner for PartitionHashUtil to verify functionality
 */
public class PartitionHashUtilRunner {
    
    public static void main(String[] args) {
        System.out.println("Testing PartitionHashUtil...");
        
        // Test basic hash function
        testBasicHashing();
        
        // Test correlation key generation
        testCorrelationKeyGeneration();
        
        // Test client partition assignment
        testClientPartitionAssignment();
        
        // Test pod name parsing
        testPodNameParsing();
        
        System.out.println("All tests passed!");
    }
    
    private static void testBasicHashing() {
        System.out.println("Testing basic hash function...");
        
        String key = "test-key";
        int partitionCount = 8;
        
        int partition = PartitionHashUtil.getPartitionForCorrelationKey(key, partitionCount);
        
        if (partition < 0 || partition >= partitionCount) {
            throw new RuntimeException("Partition out of range: " + partition);
        }
        
        // Test consistency
        int partition2 = PartitionHashUtil.getPartitionForCorrelationKey(key, partitionCount);
        if (partition != partition2) {
            throw new RuntimeException("Hash not consistent");
        }
        
        System.out.println("  Hash for '" + key + "' with " + partitionCount + " partitions: " + partition);
    }
    
    private static void testCorrelationKeyGeneration() {
        System.out.println("Testing correlation key generation...");
        
        int targetPartition = 2;
        int partitionCount = 8;
        
        try {
            String correlationKey = PartitionHashUtil.generateCorrelationKeyForPartition(
                targetPartition, partitionCount, 100);
            
            int actualPartition = PartitionHashUtil.getPartitionForCorrelationKey(correlationKey, partitionCount);
            
            if (actualPartition != targetPartition) {
                throw new RuntimeException("Generated key maps to wrong partition: " + actualPartition + " != " + targetPartition);
            }
            
            System.out.println("  Generated key '" + correlationKey + "' for partition " + targetPartition);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate correlation key", e);
        }
    }
    
    private static void testClientPartitionAssignment() {
        System.out.println("Testing client partition assignment...");
        
        // Test with more partitions than replicas
        int partition0 = PartitionHashUtil.getTargetPartitionForClient(0, 8, 3);
        int partition1 = PartitionHashUtil.getTargetPartitionForClient(1, 8, 3);
        int partition2 = PartitionHashUtil.getTargetPartitionForClient(2, 8, 3);
        
        System.out.println("  Client 0 -> Partition " + partition0);
        System.out.println("  Client 1 -> Partition " + partition1);
        System.out.println("  Client 2 -> Partition " + partition2);
        
        // Test with more replicas than partitions
        int partitionA = PartitionHashUtil.getTargetPartitionForClient(0, 2, 4);
        int partitionB = PartitionHashUtil.getTargetPartitionForClient(1, 2, 4);
        int partitionC = PartitionHashUtil.getTargetPartitionForClient(2, 2, 4);
        int partitionD = PartitionHashUtil.getTargetPartitionForClient(3, 2, 4);
        
        System.out.println("  Replica 0 -> Partition " + partitionA);
        System.out.println("  Replica 1 -> Partition " + partitionB);
        System.out.println("  Replica 2 -> Partition " + partitionC);
        System.out.println("  Replica 3 -> Partition " + partitionD);
    }
    
    private static void testPodNameParsing() {
        System.out.println("Testing pod name parsing...");
        
        int id1 = PartitionHashUtil.extractPodIdFromName("benchmark-0");
        int id2 = PartitionHashUtil.extractPodIdFromName("benchmark-5");
        int id3 = PartitionHashUtil.extractPodIdFromName("my-app-42");
        int id4 = PartitionHashUtil.extractPodIdFromName("invalid-name");
        
        System.out.println("  'benchmark-0' -> " + id1);
        System.out.println("  'benchmark-5' -> " + id2);
        System.out.println("  'my-app-42' -> " + id3);
        System.out.println("  'invalid-name' -> " + id4);
        
        if (id1 != 0 || id2 != 5 || id3 != 42 || id4 != 0) {
            throw new RuntimeException("Pod name parsing failed");
        }
    }
}