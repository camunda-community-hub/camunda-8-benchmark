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
        if (StatusRuntimeException.class.isAssignableFrom(throwable.getClass())) {
            StatusRuntimeException exception = (StatusRuntimeException) throwable;
            stats.incStartedProcessInstancesException(exception.getStatus().getCode().name());
            if (Status.Code.RESOURCE_EXHAUSTED == exception.getStatus().getCode()) {
                stats.incStartedProcessInstancesBackpressure();
                return; // ignore backpressure, as we don't want to add a big wave of retries
            }
        } else {
            stats.incStartedProcessInstancesException(throwable.getMessage());
        }
        // use normal behavior
        super.handleCommandError(command, throwable);
    }
}
