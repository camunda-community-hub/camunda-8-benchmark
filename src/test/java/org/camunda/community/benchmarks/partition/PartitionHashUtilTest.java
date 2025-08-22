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
        // Test case: 9 partitions, 3 starters - round robin distribution
        int[] partitions0 = PartitionHashUtil.getTargetPartitionsForClient(0, 9, 3);
        int[] partitions1 = PartitionHashUtil.getTargetPartitionsForClient(1, 9, 3);
        int[] partitions2 = PartitionHashUtil.getTargetPartitionsForClient(2, 9, 3);
        
        assertArrayEquals(new int[]{1, 4, 7}, partitions0);
        assertArrayEquals(new int[]{2, 5, 8}, partitions1);
        assertArrayEquals(new int[]{3, 6, 9}, partitions2);
        
        // Test case: 6 partitions, 2 starters
        int[] client0_6p2s = PartitionHashUtil.getTargetPartitionsForClient(0, 6, 2);
        int[] client1_6p2s = PartitionHashUtil.getTargetPartitionsForClient(1, 6, 2);
        
        assertArrayEquals(new int[]{1, 3, 5}, client0_6p2s);
        assertArrayEquals(new int[]{2, 4, 6}, client1_6p2s);
        
        // Test case: more starters than partitions
        int[] client0_3p5s = PartitionHashUtil.getTargetPartitionsForClient(0, 3, 5);
        int[] client1_3p5s = PartitionHashUtil.getTargetPartitionsForClient(1, 3, 5);
        int[] client2_3p5s = PartitionHashUtil.getTargetPartitionsForClient(2, 3, 5);
        int[] client3_3p5s = PartitionHashUtil.getTargetPartitionsForClient(3, 3, 5);
        int[] client4_3p5s = PartitionHashUtil.getTargetPartitionsForClient(4, 3, 5);
        
        assertArrayEquals(new int[]{1}, client0_3p5s);
        assertArrayEquals(new int[]{2}, client1_3p5s);
        assertArrayEquals(new int[]{3}, client2_3p5s);
        assertArrayEquals(new int[]{1}, client3_3p5s);
        assertArrayEquals(new int[]{2}, client4_3p5s);
        
        // Test case: uneven distribution (10 partitions, 3 starters)
        int[] client0_10p3s = PartitionHashUtil.getTargetPartitionsForClient(0, 10, 3);
        int[] client1_10p3s = PartitionHashUtil.getTargetPartitionsForClient(1, 10, 3);
        int[] client2_10p3s = PartitionHashUtil.getTargetPartitionsForClient(2, 10, 3);
        
        assertArrayEquals(new int[]{1, 4, 7, 10}, client0_10p3s); // Gets one extra
        assertArrayEquals(new int[]{2, 5, 8}, client1_10p3s);
        assertArrayEquals(new int[]{3, 6, 9}, client2_10p3s);
        
        // Test case: 100 partitions, 4 clients - should distribute evenly (25 each)
        int[] client0_100p4s = PartitionHashUtil.getTargetPartitionsForClient(0, 100, 4);
        int[] client1_100p4s = PartitionHashUtil.getTargetPartitionsForClient(1, 100, 4);
        int[] client2_100p4s = PartitionHashUtil.getTargetPartitionsForClient(2, 100, 4);
        int[] client3_100p4s = PartitionHashUtil.getTargetPartitionsForClient(3, 100, 4);
        
        // Each client should get exactly 25 partitions (100/4 = 25, no remainder)
        assertEquals(25, client0_100p4s.length, "Client 0 should get 25 partitions");
        assertEquals(25, client1_100p4s.length, "Client 1 should get 25 partitions");
        assertEquals(25, client2_100p4s.length, "Client 2 should get 25 partitions");
        assertEquals(25, client3_100p4s.length, "Client 3 should get 25 partitions");
        
        // Verify the round-robin distribution pattern
        // Client 0 gets: 1, 5, 9, 13, ..., 97
        // Client 1 gets: 2, 6, 10, 14, ..., 98
        // Client 2 gets: 3, 7, 11, 15, ..., 99
        // Client 3 gets: 4, 8, 12, 16, ..., 100
        for (int i = 0; i < 25; i++) {
            assertEquals(1 + i * 4, client0_100p4s[i], "Client 0 partition " + i);
            assertEquals(2 + i * 4, client1_100p4s[i], "Client 1 partition " + i);
            assertEquals(3 + i * 4, client2_100p4s[i], "Client 2 partition " + i);
            assertEquals(4 + i * 4, client3_100p4s[i], "Client 3 partition " + i);
        }
        
        // Verify all partitions are covered exactly once
        boolean[] coveredPartitions = new boolean[101]; // 1-indexed, so size 101
        for (int partition : client0_100p4s) coveredPartitions[partition] = true;
        for (int partition : client1_100p4s) coveredPartitions[partition] = true;
        for (int partition : client2_100p4s) coveredPartitions[partition] = true;
        for (int partition : client3_100p4s) coveredPartitions[partition] = true;
        
        // Check that partitions 1-100 are all covered
        for (int i = 1; i <= 100; i++) {
            assertTrue(coveredPartitions[i], "Partition " + i + " should be covered");
        }
        // Partition 0 should not be covered (partitions are 1-indexed)
        assertFalse(coveredPartitions[0], "Partition 0 should not be covered");
    }


    @Test
    void testExtractClientIdFromName() {
        // Test various client name formats
        assertEquals(0, PartitionHashUtil.extractClientIdFromName("benchmark-0"));
        assertEquals(100, PartitionHashUtil.extractClientIdFromName("benchmark-100"));
        assertEquals(42, PartitionHashUtil.extractClientIdFromName("my-app-42"));
        assertEquals(5, PartitionHashUtil.extractClientIdFromName("my-benchmark-app-5"));
        
        // Edge cases
        assertThrows(IllegalArgumentException.class, () -> PartitionHashUtil.extractClientIdFromName(""));
        assertThrows(IllegalArgumentException.class, () -> PartitionHashUtil.extractClientIdFromName(null));
        assertThrows(IllegalArgumentException.class, () -> PartitionHashUtil.extractClientIdFromName("no-number"));
        assertThrows(IllegalArgumentException.class, () -> PartitionHashUtil.extractClientIdFromName("ends-with-dash-"));
    }

    @Test
    void testSelectRandomPartition() {
        // Test with single partition
        int[] singlePartition = {5};
        assertEquals(5, PartitionHashUtil.selectRandomPartition(singlePartition));
        
        // Test with multiple partitions - should return one of them
        int[] multiplePartitions = {1, 4, 7};
        for (int i = 0; i < 10; i++) {
            int selected = PartitionHashUtil.selectRandomPartition(multiplePartitions);
            assertTrue(selected == 1 || selected == 4 || selected == 7, 
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