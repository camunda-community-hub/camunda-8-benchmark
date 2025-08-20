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
     * Determines which partitions this client should be responsible for
     * based on its client ID and the total number of starters using round-robin.
     * 
     * @param clientId The client ID (numeric part from starter name)
     * @param partitionCount Total number of partitions
     * @param numberOfStarters Total number of client starters
     * @return Array of partition IDs this client should handle
     */
    public static int[] getTargetPartitionsForClient(int clientId, int partitionCount, int numberOfStarters) {
        if (numberOfStarters >= partitionCount) {
            // More starters than partitions - each client gets at most one partition
            if (clientId < partitionCount) {
                return new int[]{clientId};
            } else {
                return new int[0]; // No partitions for this client
            }
        } else {
            // Fewer starters than partitions - distribute round-robin
            int partitionsPerStarter = partitionCount / numberOfStarters;
            int remainingPartitions = partitionCount % numberOfStarters;
            
            // Calculate how many partitions this client gets
            int numPartitions = partitionsPerStarter;
            if (clientId < remainingPartitions) {
                numPartitions++; // This client gets one extra partition
            }
            
            // Calculate starting partition for this client
            int startPartition = clientId * partitionsPerStarter + Math.min(clientId, remainingPartitions);
            
            int[] partitions = new int[numPartitions];
            for (int i = 0; i < numPartitions; i++) {
                partitions[i] = startPartition + i;
            }
            
            return partitions;
        }
    }
    
    /**
     * Legacy method for backward compatibility - returns the first partition only.
     * 
     * @deprecated Use getTargetPartitionsForClient instead for multiple partition support
     */
    @Deprecated
    public static int getTargetPartitionForClient(int podId, int partitionCount, int replicas) {
        int[] partitions = getTargetPartitionsForClient(podId, partitionCount, replicas);
        return partitions.length > 0 ? partitions[0] : 0;
    }

    /**
     * Extracts numeric client ID from a client name.
     * 
     * @param clientName Client name like "benchmark-0", "benchmark-1", etc.
     * @return The numeric ordinal, or 0 if parsing fails
     */
    public static int extractClientIdFromName(String clientName) {
        if (clientName == null || clientName.isEmpty()) {
            return 0;
        }
        
        int lastDash = clientName.lastIndexOf('-');
        if (lastDash == -1 || lastDash == clientName.length() - 1) {
            return 0;
        }
        
        try {
            return Integer.parseInt(clientName.substring(lastDash + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * Randomly selects one of the target partitions for message publishing.
     * This allows load balancing across all partitions assigned to a client.
     * 
     * @param targetPartitions Array of partition IDs this client should handle
     * @return A randomly selected partition from the target partitions
     */
    public static int selectRandomPartition(int[] targetPartitions) {
        if (targetPartitions.length == 0) {
            return 0;
        }
        if (targetPartitions.length == 1) {
            return targetPartitions[0];
        }
        
        int randomIndex = (int) (Math.random() * targetPartitions.length);
        return targetPartitions[randomIndex];
    }
}