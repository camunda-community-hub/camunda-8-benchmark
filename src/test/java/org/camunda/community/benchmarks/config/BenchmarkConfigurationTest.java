package org.camunda.community.benchmarks.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify BenchmarkConfiguration works with partition pinning properties
 */
public class BenchmarkConfigurationTest {
    
    @Test
    void testDefaultValues() {
        BenchmarkConfiguration config = new BenchmarkConfiguration();
        
        // Test default values for partition pinning
        assertFalse(config.isEnablePartitionPinning(), "Default partition pinning should be false");
        assertEquals(1, config.getPartitionCount(), "Default partition count should be 1");
        assertEquals(1, config.getNumberOfStarters(), "Default numberOfStarters should be 1");
        assertEquals(1000, config.getCorrelationKeyMaxAttempts(), "Default correlationKeyMaxAttempts should be 1000");
    }
    
    @Test
    void testPartitionPinningConfig() {
        BenchmarkConfiguration config = new BenchmarkConfiguration();
        
        // Set partition pinning values
        config.setEnablePartitionPinning(true);
        config.setStarterId("starter-2");
        config.setPartitionCount(9);
        config.setNumberOfStarters(3);
        config.setCorrelationKeyMaxAttempts(500);
        
        // Verify values
        assertTrue(config.isEnablePartitionPinning(), "Partition pinning should be enabled");
        assertEquals("starter-2", config.getStarterId(), "Starter ID should be 'starter-2'");
        assertEquals(9, config.getPartitionCount(), "Partition count should be 9");
        assertEquals(3, config.getNumberOfStarters(), "NumberOfStarters should be 3");
        assertEquals(500, config.getCorrelationKeyMaxAttempts(), "CorrelationKeyMaxAttempts should be 500");
    }
}