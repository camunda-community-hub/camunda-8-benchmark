package org.camunda.community.benchmarks;

import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.camunda.zeebe.spring.client.jobhandling.CommandWrapper;
import io.camunda.zeebe.spring.client.jobhandling.DefaultCommandExceptionHandlingStrategy;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;

@Component
public class BenchmarkStartDiExceptionHandlingStrategy extends DefaultCommandExceptionHandlingStrategy  {

    @Autowired
    private StatisticsCollector stats;

    public BenchmarkStartDiExceptionHandlingStrategy(@Autowired BackoffSupplier backoffSupplier, @Autowired ScheduledExecutorService scheduledExecutorService) {
        super(backoffSupplier, scheduledExecutorService);
    }

    public void handleCommandError(CommandWrapper command, Throwable throwable) {
        if (StatusRuntimeException.class.isAssignableFrom(throwable.getClass())) {
            StatusRuntimeException exception = (StatusRuntimeException) throwable;
            stats.incStartedDecisionInstancesException(exception.getStatus().getCode().name());
            // Sometimes if the Processing Latency gets higher, an UNAVAILABLE status code is received, instead of RESOURCE EXHAUSTED, Counting this also as backpressure & avoiding retries
            // This is quite frequent, since as of 8.2.0, the DMN is parsed with each evaluation request & has significant effect on the latency & performance
            if (Status.Code.RESOURCE_EXHAUSTED == exception.getStatus().getCode() || Status.Code.UNAVAILABLE == exception.getStatus().getCode()) {
                stats.incStartedDecisionInstancesBackpressure();
                return; // ignore backpressure, as we don't want to add a big wave of retries
            }
        } else {
            stats.incStartedDecisionInstancesException(throwable.getMessage());
        }
        // use normal behavior
        super.handleCommandError(command, throwable);
    }
}
