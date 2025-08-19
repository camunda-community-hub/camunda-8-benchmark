package org.camunda.community.benchmarks.partition;

import java.util.UUID;

/**
 * Utility class for generating correlation keys that map to specific partitions
 * using Zeebe's partition distribution algorithm.
 */
public class PartitionHashUtil {

    /**
     * Generates a correlation key that will hash to the specified target partition.
     * 
     * This uses the same hash function that Zeebe uses internally to distribute
     * messages across partitions: djb2 hash algorithm.
     * 
     * @param targetPartition The partition ID to target (0-based)
     * @param partitionCount Total number of partitions
     * @param maxAttempts Maximum number of attempts to find a suitable key
     * @return A correlation key that hashes to the target partition
     * @throws IllegalStateException if no suitable key is found within maxAttempts
     */
    public static String generateCorrelationKeyForPartition(int targetPartition, int partitionCount, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String candidateKey = "benchmark-" + UUID.randomUUID().toString();
            int partition = getPartitionForCorrelationKey(candidateKey, partitionCount);
            if (partition == targetPartition) {
                return candidateKey;
            }
        }
        throw new IllegalStateException(
            String.format("Could not generate correlation key for partition %d after %d attempts", 
                         targetPartition, maxAttempts));
    }

    /**
     * Calculates which partition a correlation key would be routed to.
     * 
     * This implements the same djb2 hash function used by Zeebe.
     * 
     * @param correlationKey The correlation key to hash
     * @param partitionCount Total number of partitions
     * @return The partition ID (0-based) that this key would route to
     */
    public static int getPartitionForCorrelationKey(String correlationKey, int partitionCount) {
        if (correlationKey == null) {
            throw new IllegalArgumentException("Correlation key cannot be null");
        }
        if (partitionCount <= 0) {
            throw new IllegalArgumentException("Partition count must be positive");
        }
        
        // djb2 hash algorithm - same as used by Zeebe
        long hash = 5381;
        byte[] bytes = correlationKey.getBytes();
        
        for (byte b : bytes) {
            hash = ((hash << 5) + hash) + (b & 0xff);
        }
        
        // Ensure positive result and mod by partition count
        return (int) ((hash & 0x7fffffffL) % partitionCount);
    }

    /**
     * Determines which partition this client should be responsible for
     * based on its pod ID and the total number of replicas.
     * 
     * @param podId The pod ID (numeric part from StatefulSet ordinal)
     * @param partitionCount Total number of partitions
     * @param replicas Total number of client replicas
     * @return The partition ID this client should handle
     */
    public static int getTargetPartitionForClient(int podId, int partitionCount, int replicas) {
        if (replicas > partitionCount) {
            // More replicas than partitions - some clients will share partitions
            return podId % partitionCount;
        } else {
            // Distribute partitions across replicas
            return (podId * partitionCount) / replicas;
        }
    }

    /**
     * Extracts numeric pod ID from a Kubernetes StatefulSet pod name.
     * 
     * @param podName Pod name like "benchmark-0", "benchmark-1", etc.
     * @return The numeric ordinal, or 0 if parsing fails
     */
    public static int extractPodIdFromName(String podName) {
        if (podName == null || podName.isEmpty()) {
            return 0;
        }
        
        int lastDash = podName.lastIndexOf('-');
        if (lastDash == -1 || lastDash == podName.length() - 1) {
            return 0;
        }
        
        try {
            return Integer.parseInt(podName.substring(lastDash + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}