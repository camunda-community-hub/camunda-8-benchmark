package org.camunda.community.benchmarks;

import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.camunda.zeebe.spring.client.jobhandling.CommandWrapper;
import io.camunda.zeebe.spring.client.jobhandling.DefaultCommandExceptionHandlingStrategy;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;

@Primary
@Component
public class BenchmarkCompleteJobExceptionHandlingStrategy extends DefaultCommandExceptionHandlingStrategy  {

    private static final Logger LOG = LoggerFactory.getLogger(BenchmarkCompleteJobExceptionHandlingStrategy.class);

    @Autowired
    private StatisticsCollector stats;

    public BenchmarkCompleteJobExceptionHandlingStrategy(@Autowired BackoffSupplier backoffSupplier, @Autowired ScheduledExecutorService scheduledExecutorService) {
        super(backoffSupplier, scheduledExecutorService);
    }

    public void handleCommandError(CommandWrapper command, Throwable throwable) {
       
        if (StatusRuntimeException.class.isAssignableFrom(throwable.getClass())) {
            StatusRuntimeException exception = (StatusRuntimeException) throwable;
            logException(throwable, stats.incCompletedJobsException(exception.getStatus().getCode().name()));
            
            /* Backpressure on Job completion cannot happen at the moment (whitelisted)
            if (Status.Code.RESOURCE_EXHAUSTED == exception.getStatus().getCode()) {
                stats.getBackpressureOnJobCompleteMeter().mark();
                return;
            }*/
        } else {
            logException(throwable, stats.incCompletedJobsException(throwable.getMessage()));
        }

        // use normal behavior, e.g. increasing back-off for backpressure
        super.handleCommandError(command, throwable);
    }
    
    /**
     * Log the exception with full stacktrace on first occurrence, 
     * or just the message on subsequent occurrences.
     */
    private void logException(Throwable throwable, double count) {
        if (count == 1.0) {
            // First occurrence - log with full stacktrace
            LOG.error("Job Exception (FIRST OCCURRENCE)", throwable);
        } else {
            // Subsequent occurrence - log only the message
            LOG.warn("Job Exception (REPEATED): {}", throwable.getMessage());
        }
    }
}
