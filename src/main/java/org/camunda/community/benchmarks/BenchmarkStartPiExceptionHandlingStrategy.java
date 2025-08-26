package org.camunda.community.benchmarks;

import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.camunda.zeebe.spring.client.jobhandling.CommandWrapper;
import io.camunda.zeebe.spring.client.jobhandling.DefaultCommandExceptionHandlingStrategy;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;

@Component
public class BenchmarkStartPiExceptionHandlingStrategy extends DefaultCommandExceptionHandlingStrategy  {

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
            stats.incStartedProcessInstancesException(statusRuntimeException.getStatus().getCode().name());
            if (Status.Code.RESOURCE_EXHAUSTED == statusRuntimeException.getStatus().getCode()) {
                stats.incStartedProcessInstancesBackpressure();
                return; // ignore backpressure, as we don't want to add a big wave of retries
            // } else if (Status.Code.FAILED_PRECONDITION == statusRuntimeException.getStatus().getCode()) {
            //     System.err.println("Received FAILED_PRECONDITION from broker - shutting down to avoid further issues.");
            //     throwable.printStackTrace();
            //     System.exit(Status.Code.FAILED_PRECONDITION.ordinal());
            //     return;
            }
        } else {
            stats.incStartedProcessInstancesException(throwable.getMessage());
        }
        // use normal behavior
        super.handleCommandError(command, throwable);
    }
}
