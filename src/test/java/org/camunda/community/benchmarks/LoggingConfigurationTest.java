package org.camunda.community.benchmarks;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test to verify that logging configuration properly suppresses verbose Zeebe client logs
 * while preserving ERROR level logging for statistics collection.
 */
class LoggingConfigurationTest {

    @Test
    void testZeebeClientLoggersAreConfiguredToErrorLevel() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // Verify that Zeebe client loggers are set to ERROR level to suppress verbose logging
        Logger jobPollerLogger = context.getLogger("io.camunda.zeebe.client.job.poller");
        Logger jobWorkerLogger = context.getLogger("io.camunda.zeebe.client.job.worker");
        Logger zeebeClientLogger = context.getLogger("io.camunda.zeebe.client");
        Logger grpcLogger = context.getLogger("io.grpc");
        Logger nettyLogger = context.getLogger("io.netty");

        // These should be set to ERROR level to suppress verbose failure messages
        assertEquals(Level.ERROR, jobPollerLogger.getLevel());
        assertEquals(Level.ERROR, jobWorkerLogger.getLevel());
        assertEquals(Level.ERROR, zeebeClientLogger.getLevel());
        assertEquals(Level.ERROR, grpcLogger.getLevel());
        assertEquals(Level.ERROR, nettyLogger.getLevel());
    }

    @Test
    void testApplicationLoggersRemainAtInfoLevel() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // Verify that application loggers remain at INFO level for normal operation
        Logger springLogger = context.getLogger("org.springframework");
        Logger camundaLogger = context.getLogger("org.camunda");

        assertEquals(Level.INFO, springLogger.getLevel());
        assertEquals(Level.INFO, camundaLogger.getLevel());
    }
}