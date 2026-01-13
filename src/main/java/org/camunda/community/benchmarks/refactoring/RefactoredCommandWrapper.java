package org.camunda.community.benchmarks.refactoring;

import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.jobhandling.CommandWrapper;
import io.camunda.client.jobhandling.DefaultCommandExceptionHandlingStrategy;
import io.camunda.client.metrics.MetricsRecorder;

import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Copied from CommandWrapper from spring-zeebe. Refactor over there to be able to use built-in stuff directly
 */
public class RefactoredCommandWrapper extends CommandWrapper {

        private final FinalCommandStep<?> command;
        private final long deadline;
        private final String entityLogInfo;
        private final DefaultCommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
        private long currentRetryDelay = 50L;
        private int invocationCounter = 0;
        private final int maxRetries = 20;

        public RefactoredCommandWrapper(FinalCommandStep<?> command, long deadline, String entityLogInfo, DefaultCommandExceptionHandlingStrategy commandExceptionHandlingStrategy, MetricsRecorder metricsRecorder) {
            super(command, null, commandExceptionHandlingStrategy, metricsRecorder, 20);
            this.command = command;
            this.deadline = deadline;
            this.entityLogInfo = entityLogInfo;
            this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
        }

        public void executeAsync() {
            ++this.invocationCounter;
            this.command.send().exceptionally((t) -> {
                this.commandExceptionHandlingStrategy.handleCommandError(this, t);
                return null;
            });
        }

        public void increaseBackoffUsing(BackoffSupplier backoffSupplier) {
            this.currentRetryDelay = backoffSupplier.supplyRetryDelay(this.currentRetryDelay);
        }

        public void scheduleExecutionUsing(ScheduledExecutorService scheduledExecutorService) {
            scheduledExecutorService.schedule(this::executeAsync, this.currentRetryDelay, TimeUnit.MILLISECONDS);
        }

        public String toString() {
            return "{command=" + this.command.getClass() + ", entity=" + this.entityLogInfo + ", currentRetryDelay=" + this.currentRetryDelay + '}';
        }

        public boolean hasMoreRetries() {
            if (this.jobDeadlineExceeded()) {
                return false;
            } else {
                return this.invocationCounter < this.maxRetries;
            }
        }

        public boolean jobDeadlineExceeded() {
            return Instant.now().getEpochSecond() > this.deadline;
        }

}
