package org.camunda.community.benchmarks.config;

/**
 * Simple test to verify BenchmarkConfiguration works with partition pinning properties
 */
public class BenchmarkConfigurationRunner {
    
    public static void main(String[] args) {
        System.out.println("Testing BenchmarkConfiguration...");
        
        // Test default values
        testDefaultValues();
        
        // Test partition pinning configuration
        testPartitionPinningConfig();
        
        System.out.println("Configuration tests passed!");
    }
    
    private static void testDefaultValues() {
        System.out.println("Testing default configuration values...");
        
        BenchmarkConfiguration config = new BenchmarkConfiguration();
        
        // Test default values for partition pinning
        if (config.isEnablePartitionPinning()) {
            throw new RuntimeException("Default partition pinning should be false");
        }
        
        if (config.getPartitionCount() != 1) {
            throw new RuntimeException("Default partition count should be 1");
        }
        
        if (config.getReplicas() != 1) {
            throw new RuntimeException("Default replicas should be 1");
        }
        
        System.out.println("  Default values OK");
    }
    
    private static void testPartitionPinningConfig() {
        System.out.println("Testing partition pinning configuration...");
        
        BenchmarkConfiguration config = new BenchmarkConfiguration();
        
        // Set partition pinning values
        config.setEnablePartitionPinning(true);
        config.setPodId("benchmark-2");
        config.setPartitionCount(8);
        config.setReplicas(3);
        
        // Verify values
        if (!config.isEnablePartitionPinning()) {
            throw new RuntimeException("Partition pinning should be enabled");
        }
        
        if (!"benchmark-2".equals(config.getPodId())) {
            throw new RuntimeException("Pod ID should be 'benchmark-2'");
        }
        
        if (config.getPartitionCount() != 8) {
            throw new RuntimeException("Partition count should be 8");
        }
        
        if (config.getReplicas() != 3) {
            throw new RuntimeException("Replicas should be 3");
        }
        
        System.out.println("  Partition pinning configuration OK");
    }
}