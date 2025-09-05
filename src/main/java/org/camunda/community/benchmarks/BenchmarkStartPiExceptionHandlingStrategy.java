package org.camunda.community.benchmarks;

import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.camunda.zeebe.spring.client.jobhandling.CommandWrapper;
import io.camunda.zeebe.spring.client.jobhandling.DefaultCommandExceptionHandlingStrategy;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;

@Component
public class BenchmarkStartPiExceptionHandlingStrategy extends DefaultCommandExceptionHandlingStrategy  {

    private static final Logger LOG = LoggerFactory.getLogger(BenchmarkStartPiExceptionHandlingStrategy.class);
    
    @Autowired
    private StatisticsCollector stats;

    public BenchmarkStartPiExceptionHandlingStrategy(@Autowired BackoffSupplier backoffSupplier, @Autowired ScheduledExecutorService scheduledExecutorService) {
        super(backoffSupplier, scheduledExecutorService);
    }

    public void handleCommandError(CommandWrapper command, Throwable throwable) {
        StatusRuntimeException statusRuntimeException = null;
        if (StatusRuntimeException.class.isAssignableFrom(throwable.getClass())) {
            statusRuntimeException = (StatusRuntimeException) throwable;
        } else if (StatusRuntimeException.class.isAssignableFrom(throwable.getCause().getClass())) {
            statusRuntimeException = (StatusRuntimeException) throwable.getCause();
        }
        if (statusRuntimeException != null) {            
            logException(throwable, stats.incStartedProcessInstancesException(statusRuntimeException.getStatus().getCode().name()));
            
            if (Status.Code.RESOURCE_EXHAUSTED == statusRuntimeException.getStatus().getCode()) {
                stats.incStartedProcessInstancesBackpressure();
                return; // ignore backpressure, as we don't want to add a big wave of retries
            }
        } else {
            logException(throwable, stats.incStartedProcessInstancesException(throwable.getMessage()));
        }
        
        // use normal behavior
        super.handleCommandError(command, throwable);
    }
    
    /**
     * Log the exception with full stacktrace on first occurrence, 
     * or just the message on subsequent occurrences.
     */
    private void logException(Throwable throwable, double count) {
        if (count == 1.0) {
            // First occurrence - log with full stacktrace
            LOG.error("PI Exception (FIRST OCCURRENCE)", throwable);
        } else {
            // Subsequent occurrence - log only the message
            LOG.warn("PI Exception (REPEATED): {}", throwable.getMessage());
        }
    }
}
