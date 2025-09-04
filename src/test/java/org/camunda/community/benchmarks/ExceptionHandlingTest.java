package org.camunda.community.benchmarks;

import static org.mockito.Mockito.*;

import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.camunda.zeebe.spring.client.jobhandling.CommandWrapper;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ScheduledExecutorService;

@ExtendWith(MockitoExtension.class)
class ExceptionHandlingTest {

    @Mock
    private StatisticsCollector statsCollector;

    @Mock
    private BackoffSupplier backoffSupplier;

    @Mock
    private ScheduledExecutorService scheduledExecutorService;

    @Mock
    private CommandWrapper commandWrapper;

    private BenchmarkCompleteJobExceptionHandlingStrategy completeJobStrategy;
    private BenchmarkStartPiExceptionHandlingStrategy startPiStrategy;

    @BeforeEach
    void setUp() {
        completeJobStrategy = new BenchmarkCompleteJobExceptionHandlingStrategy(backoffSupplier, scheduledExecutorService);
        completeJobStrategy.stats = statsCollector;

        startPiStrategy = new BenchmarkStartPiExceptionHandlingStrategy(backoffSupplier, scheduledExecutorService);
        startPiStrategy.stats = statsCollector;
    }

    @Test
    void testCompleteJobExceptionHandling_StatusRuntimeException() {
        // Given
        StatusRuntimeException exception = new StatusRuntimeException(Status.DEADLINE_EXCEEDED);

        // When
        completeJobStrategy.handleCommandError(commandWrapper, exception);

        // Then
        verify(statsCollector).incCompletedJobsException("DEADLINE_EXCEEDED");
    }

    @Test
    void testCompleteJobExceptionHandling_NotFound() {
        // Given
        StatusRuntimeException exception = new StatusRuntimeException(Status.NOT_FOUND);

        // When
        completeJobStrategy.handleCommandError(commandWrapper, exception);

        // Then
        verify(statsCollector).incCompletedJobsException("NOT_FOUND");
    }

    @Test
    void testCompleteJobExceptionHandling_OtherException() {
        // Given
        RuntimeException exception = new RuntimeException("Some other error");

        // When
        completeJobStrategy.handleCommandError(commandWrapper, exception);

        // Then
        verify(statsCollector).incCompletedJobsException("Some other error");
    }

    @Test
    void testStartPiExceptionHandling_StatusRuntimeException() {
        // Given
        StatusRuntimeException exception = new StatusRuntimeException(Status.DEADLINE_EXCEEDED);

        // When
        startPiStrategy.handleCommandError(commandWrapper, exception);

        // Then
        verify(statsCollector).incStartedProcessInstancesException("DEADLINE_EXCEEDED");
    }

    @Test
    void testStartPiExceptionHandling_ResourceExhausted() {
        // Given
        StatusRuntimeException exception = new StatusRuntimeException(Status.RESOURCE_EXHAUSTED);

        // When
        startPiStrategy.handleCommandError(commandWrapper, exception);

        // Then
        verify(statsCollector).incStartedProcessInstancesException("RESOURCE_EXHAUSTED");
        verify(statsCollector).incStartedProcessInstancesBackpressure();
    }

    @Test
    void testStartPiExceptionHandling_NestedStatusRuntimeException() {
        // Given
        StatusRuntimeException nestedEx = new StatusRuntimeException(Status.UNAVAILABLE);
        RuntimeException exception = new RuntimeException("Wrapper", nestedEx);

        // When
        startPiStrategy.handleCommandError(commandWrapper, exception);

        // Then
        verify(statsCollector).incStartedProcessInstancesException("UNAVAILABLE");
    }
}