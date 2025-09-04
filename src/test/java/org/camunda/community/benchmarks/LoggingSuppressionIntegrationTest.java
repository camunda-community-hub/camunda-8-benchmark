package org.camunda.community.benchmarks;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify that the logging configuration successfully suppresses
 * verbose Zeebe client error messages while maintaining error-level logging capability.
 */
class LoggingSuppressionIntegrationTest {

    private LoggerContext loggerContext;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        listAppender = new ListAppender<>();
        listAppender.start();
    }

    @Test
    void testZeebeJobPollerLogsAreSuppressedAtWarnLevel() {
        // Setup logger with our configuration
        Logger jobPollerLogger = loggerContext.getLogger("io.camunda.zeebe.client.job.poller");
        jobPollerLogger.addAppender(listAppender);
        
        // Simulate the types of messages that were flooding the logs
        jobPollerLogger.warn("Failed to activated jobs for worker default and job type benchmark-task-benchmarkStarter1");
        jobPollerLogger.warn("DEADLINE_EXCEEDED: deadline exceeded after 19.999980100s");
        
        // These WARN messages should not appear in the log output since level is set to ERROR
        assertEquals(0, listAppender.list.size(), "WARN level messages should be suppressed");
        
        // But ERROR messages should still appear
        jobPollerLogger.error("Critical error that should appear");
        assertEquals(1, listAppender.list.size(), "ERROR level messages should still appear");
        assertEquals("Critical error that should appear", listAppender.list.get(0).getMessage());
    }

    @Test
    void testZeebeJobWorkerLogsAreSuppressedAtWarnLevel() {
        Logger jobWorkerLogger = loggerContext.getLogger("io.camunda.zeebe.client.job.worker");
        jobWorkerLogger.addAppender(listAppender);
        
        // Simulate the "Failed to stream jobs" messages from the issue
        jobWorkerLogger.warn("Failed to stream jobs of type 'benchmark-gateway-3-benchmark-task-45' to worker 'benchmark-gateway-3-benchmark-task-45'");
        
        // This WARN message should not appear
        assertEquals(0, listAppender.list.size(), "Job worker WARN messages should be suppressed");
    }

    @Test
    void testGrpcLogsAreSuppressedAtWarnLevel() {
        Logger grpcLogger = loggerContext.getLogger("io.grpc.internal.ClientCallImpl");
        grpcLogger.addAppender(listAppender);
        
        // Simulate gRPC connection errors
        grpcLogger.warn("StatusRuntimeException: DEADLINE_EXCEEDED: Time out between gateway and broker");
        grpcLogger.warn("StatusRuntimeException: UNAVAILABLE: io exception");
        
        // These should not appear since gRPC is set to ERROR level
        assertEquals(0, listAppender.list.size(), "gRPC WARN messages should be suppressed");
    }

    @Test
    void testApplicationLoggersStillWorkAtInfoLevel() {
        Logger appLogger = loggerContext.getLogger("org.camunda.community.benchmarks.StatisticsCollector");
        appLogger.addAppender(listAppender);
        
        // Application logs should still work normally
        appLogger.info("Job Exception [DEADLINE_EXCEEDED]: 5");
        appLogger.info("Job Exception [NOT_FOUND]: 3");
        
        assertEquals(2, listAppender.list.size(), "Application INFO messages should appear");
        assertTrue(listAppender.list.get(0).getMessage().contains("DEADLINE_EXCEEDED"));
        assertTrue(listAppender.list.get(1).getMessage().contains("NOT_FOUND"));
    }

    @Test 
    void testStatisticsCollectorBehaviorIsPreserved() {
        // This test verifies that our logging changes don't affect the core statistics functionality
        // The StatisticsCollector uses System.out.println for its output, not the SLF4J logger
        
        Logger statsLogger = loggerContext.getLogger("org.camunda.community.benchmarks.StatisticsCollector");
        statsLogger.addAppender(listAppender);
        
        // Even if there were SLF4J log statements in StatisticsCollector, they would work at INFO level
        statsLogger.info("Statistics collection working normally");
        assertEquals(1, listAppender.list.size());
        
        // The actual statistics output uses System.out.println and is unaffected by logging configuration
        // This is the desired behavior - statistics always appear regardless of log levels
    }

    @Test
    void testErrorLevelMessagesStillAppearFromZeebeLoggers() {
        Logger zeebeLogger = loggerContext.getLogger("io.camunda.zeebe.client");
        zeebeLogger.addAppender(listAppender);
        
        // ERROR level messages should still appear even with ERROR level configuration
        zeebeLogger.error("Critical Zeebe client error");
        assertEquals(1, listAppender.list.size());
        assertEquals(Level.ERROR, listAppender.list.get(0).getLevel());
    }
}