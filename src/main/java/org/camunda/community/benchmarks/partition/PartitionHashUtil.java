package org.camunda.community.benchmarks.partition;

import java.util.UUID;

import static io.camunda.zeebe.protocol.impl.SubscriptionUtil.getSubscriptionPartitionId;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

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
        if (partitionCount == 1) {
            return "benchmark-" + UUID.randomUUID();
        }
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String candidateKey = "benchmark-" + UUID.randomUUID();
          
            int partition = getPartitionForCorrelationKey(candidateKey, partitionCount);
            if (partition == targetPartition) {
                //System.out.println("Attempt " + attempt + ": key=" + candidateKey + " -> partition=" + partition);
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
        return getSubscriptionPartitionId(wrapString(correlationKey), partitionCount);
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
            // Use modulo to cycle through partitions: client 0->partition 1, client 1->partition 2, etc.
            int targetPartition = (clientId % partitionCount) + 1; // +1 because partition IDs start at 1
            return new int[]{targetPartition};
        } else {
            // Fewer starters than partitions - distribute round-robin
            // Client N gets partitions: (N+1), (N+1)+numberOfStarters, (N+1)+2*numberOfStarters, etc.
            
            // Calculate how many partitions this client will get
            int partitionsPerClient = partitionCount / numberOfStarters;
            int remainingPartitions = partitionCount % numberOfStarters;
            
            // Clients with ID < remainingPartitions get one extra partition
            int numPartitions = partitionsPerClient;
            if (clientId < remainingPartitions) {
                numPartitions++;
            }
            
            int[] partitions = new int[numPartitions];
            int currentPartition = clientId + 1; // Start at clientId + 1 (since partition IDs start at 1)
            
            for (int i = 0; i < numPartitions; i++) {
                partitions[i] = currentPartition;
                currentPartition += numberOfStarters; // Jump by numberOfStarters for round-robin
            }
            
            return partitions;
        }
    }

    /**
     * Extracts numeric client ID from a client name.
     * 
     * @param clientName Client name like "benchmark-0", "benchmark-1", etc.
     * @return The numeric ordinal, or 0 if parsing fails
     */
    public static int extractClientIdFromName(String clientName) {
        if (clientName == null || clientName.isEmpty()) {
            throw new IllegalArgumentException("Client name cannot be null or empty");
        }
        
        int lastDash = clientName.lastIndexOf('-');
        if (lastDash == -1 || lastDash == clientName.length() - 1) {
            throw new IllegalArgumentException("Client name must end with a numeric ID after a dash");
        }
        
        try {
            return Integer.parseInt(clientName.substring(lastDash + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Client name must end with a valid numeric ID", e);
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