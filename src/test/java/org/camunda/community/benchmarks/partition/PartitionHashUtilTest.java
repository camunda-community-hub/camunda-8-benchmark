package org.camunda.community.benchmarks.partition;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class PartitionHashUtilTest {

    @Test
    void testGetPartitionForCorrelationKey() {
        String correlationKey = "test-key";
        int partitionCount = 3;
        
        int partition = PartitionHashUtil.getPartitionForCorrelationKey(correlationKey, partitionCount);
        
        assertTrue(partition >= 0, "Partition should be non-negative");
        assertTrue(partition < partitionCount, "Partition should be less than partition count");
        
        // Test consistency - same key should always return same partition
        int partition2 = PartitionHashUtil.getPartitionForCorrelationKey(correlationKey, partitionCount);
        assertEquals(partition, partition2, "Same key should always return same partition");
    }

    @Test
    void testGetPartitionForCorrelationKey_NullKey() {
        assertThrows(IllegalArgumentException.class, () -> {
            PartitionHashUtil.getPartitionForCorrelationKey(null, 3);
        });
    }

    @Test
    void testGetPartitionForCorrelationKey_InvalidPartitionCount() {
        assertThrows(IllegalArgumentException.class, () -> {
            PartitionHashUtil.getPartitionForCorrelationKey("test", 0);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            PartitionHashUtil.getPartitionForCorrelationKey("test", -1);
        });
    }

    @Test
    void testGenerateCorrelationKeyForPartition() {
        int targetPartition = 1;
        int partitionCount = 3;
        int maxAttempts = 100;
        
        String correlationKey = PartitionHashUtil.generateCorrelationKeyForPartition(
            targetPartition, partitionCount, maxAttempts);
        
        assertNotNull(correlationKey, "Generated key should not be null");
        assertTrue(correlationKey.startsWith("benchmark-"), "Key should have expected prefix");
        
        // Verify the generated key actually maps to the target partition
        int actualPartition = PartitionHashUtil.getPartitionForCorrelationKey(correlationKey, partitionCount);
        assertEquals(targetPartition, actualPartition, "Generated key should map to target partition");
    }

    @Test
    void testGetTargetPartitionForClient() {
        // Test case: more partitions than replicas
        assertEquals(0, PartitionHashUtil.getTargetPartitionForClient(0, 4, 2));
        assertEquals(2, PartitionHashUtil.getTargetPartitionForClient(1, 4, 2));
        
        // Test case: more replicas than partitions  
        assertEquals(0, PartitionHashUtil.getTargetPartitionForClient(0, 2, 4));
        assertEquals(1, PartitionHashUtil.getTargetPartitionForClient(1, 2, 4));
        assertEquals(0, PartitionHashUtil.getTargetPartitionForClient(2, 2, 4));
        assertEquals(1, PartitionHashUtil.getTargetPartitionForClient(3, 2, 4));
        
        // Test case: equal partitions and replicas
        assertEquals(0, PartitionHashUtil.getTargetPartitionForClient(0, 3, 3));
        assertEquals(1, PartitionHashUtil.getTargetPartitionForClient(1, 3, 3));
        assertEquals(2, PartitionHashUtil.getTargetPartitionForClient(2, 3, 3));
    }

    @Test
    void testExtractPodIdFromName() {
        assertEquals(0, PartitionHashUtil.extractPodIdFromName("benchmark-0"));
        assertEquals(1, PartitionHashUtil.extractPodIdFromName("benchmark-1"));
        assertEquals(42, PartitionHashUtil.extractPodIdFromName("my-app-42"));
        
        // Edge cases
        assertEquals(0, PartitionHashUtil.extractPodIdFromName(""));
        assertEquals(0, PartitionHashUtil.extractPodIdFromName(null));
        assertEquals(0, PartitionHashUtil.extractPodIdFromName("no-number"));
        assertEquals(0, PartitionHashUtil.extractPodIdFromName("ends-with-dash-"));
    }

    @Test
    void testHashDistribution() {
        // Test that different keys distribute across partitions
        int partitionCount = 8;
        boolean[] partitionsUsed = new boolean[partitionCount];
        
        for (int i = 0; i < 100; i++) {
            String key = "test-key-" + i;
            int partition = PartitionHashUtil.getPartitionForCorrelationKey(key, partitionCount);
            partitionsUsed[partition] = true;
        }
        
        // We should have used most partitions with 100 different keys
        int usedPartitions = 0;
        for (boolean used : partitionsUsed) {
            if (used) usedPartitions++;
        }
        
        assertTrue(usedPartitions >= partitionCount / 2, 
            "Should distribute across at least half the partitions");
    }
}