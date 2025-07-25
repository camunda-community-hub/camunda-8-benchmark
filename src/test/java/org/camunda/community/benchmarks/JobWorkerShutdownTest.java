package org.camunda.community.benchmarks;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class JobWorkerShutdownTest {

    @Test
    public void testShutdownFlagIsSetOnDestroy() throws Exception {
        JobWorker jobWorker = new JobWorker();
        
        // Access the private shutdown flag
        Field shutdownField = JobWorker.class.getDeclaredField("isShuttingDown");
        shutdownField.setAccessible(true);
        AtomicBoolean isShuttingDown = (AtomicBoolean) shutdownField.get(jobWorker);
        
        // Initially should not be shutting down
        assertFalse(isShuttingDown.get());
        
        // Call shutdown method
        jobWorker.shutdown();
        
        // Should now be shutting down
        assertTrue(isShuttingDown.get());
    }
}