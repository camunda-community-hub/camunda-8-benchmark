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
    void testGetTargetPartitionsForClient() {
        // Test case: 9 partitions, 3 starters - even distribution
        int[] partitions0 = PartitionHashUtil.getTargetPartitionsForClient(0, 9, 3);
        int[] partitions1 = PartitionHashUtil.getTargetPartitionsForClient(1, 9, 3);
        int[] partitions2 = PartitionHashUtil.getTargetPartitionsForClient(2, 9, 3);
        
        assertArrayEquals(new int[]{0, 1, 2}, partitions0);
        assertArrayEquals(new int[]{3, 4, 5}, partitions1);
        assertArrayEquals(new int[]{6, 7, 8}, partitions2);
        
        // Test case: 6 partitions, 2 starters
        int[] client0_6p2s = PartitionHashUtil.getTargetPartitionsForClient(0, 6, 2);
        int[] client1_6p2s = PartitionHashUtil.getTargetPartitionsForClient(1, 6, 2);
        
        assertArrayEquals(new int[]{0, 1, 2}, client0_6p2s);
        assertArrayEquals(new int[]{3, 4, 5}, client1_6p2s);
        
        // Test case: more starters than partitions
        int[] client0_3p5s = PartitionHashUtil.getTargetPartitionsForClient(0, 3, 5);
        int[] client1_3p5s = PartitionHashUtil.getTargetPartitionsForClient(1, 3, 5);
        int[] client2_3p5s = PartitionHashUtil.getTargetPartitionsForClient(2, 3, 5);
        int[] client3_3p5s = PartitionHashUtil.getTargetPartitionsForClient(3, 3, 5);
        int[] client4_3p5s = PartitionHashUtil.getTargetPartitionsForClient(4, 3, 5);
        
        assertArrayEquals(new int[]{0}, client0_3p5s);
        assertArrayEquals(new int[]{1}, client1_3p5s);
        assertArrayEquals(new int[]{2}, client2_3p5s);
        assertArrayEquals(new int[0], client3_3p5s); // No partitions for client 3
        assertArrayEquals(new int[0], client4_3p5s); // No partitions for client 4
        
        // Test case: uneven distribution (10 partitions, 3 starters)
        int[] client0_10p3s = PartitionHashUtil.getTargetPartitionsForClient(0, 10, 3);
        int[] client1_10p3s = PartitionHashUtil.getTargetPartitionsForClient(1, 10, 3);
        int[] client2_10p3s = PartitionHashUtil.getTargetPartitionsForClient(2, 10, 3);
        
        assertArrayEquals(new int[]{0, 1, 2, 3}, client0_10p3s); // Gets one extra
        assertArrayEquals(new int[]{4, 5, 6}, client1_10p3s);
        assertArrayEquals(new int[]{7, 8, 9}, client2_10p3s);
    }

    @Test
    void testGetTargetPartitionForClient_BackwardCompatibility() {
        // Test backward compatibility with deprecated method
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
    void testExtractClientIdFromName() {
        assertEquals(0, PartitionHashUtil.extractClientIdFromName("benchmark-0"));
        assertEquals(1, PartitionHashUtil.extractClientIdFromName("benchmark-1"));
        assertEquals(42, PartitionHashUtil.extractClientIdFromName("my-app-42"));
        
        // Edge cases
        assertEquals(0, PartitionHashUtil.extractClientIdFromName(""));
        assertEquals(0, PartitionHashUtil.extractClientIdFromName(null));
        assertEquals(0, PartitionHashUtil.extractClientIdFromName("no-number"));
        assertEquals(0, PartitionHashUtil.extractClientIdFromName("ends-with-dash-"));
    }

    @Test
    void testSelectRandomPartition() {
        // Test with single partition
        int[] singlePartition = {5};
        assertEquals(5, PartitionHashUtil.selectRandomPartition(singlePartition));
        
        // Test with multiple partitions - should return one of them
        int[] multiplePartitions = {1, 3, 7};
        for (int i = 0; i < 10; i++) {
            int selected = PartitionHashUtil.selectRandomPartition(multiplePartitions);
            assertTrue(selected == 1 || selected == 3 || selected == 7, 
                      "Selected partition should be one of the target partitions");
        }
        
        // Test with empty array
        int[] emptyPartitions = {};
        assertEquals(0, PartitionHashUtil.selectRandomPartition(emptyPartitions));
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