package org.camunda.community.benchmarks.flowcontrol;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared, mutable view of the current target rate (PI/s).
 * <p>
 * Both {@link Bucket4jPiScheduler} (which adjusts the rate under the {@code autoTune}
 * strategy) and {@link FlowControlInterceptor} (which needs the rate to size its
 * backpressure penalty proportionally) depend on this bean instead of on each other,
 * avoiding a circular dependency between the two.
 */
public class RateGoal {

    private final AtomicLong currentRate;

    public RateGoal(long initialRate) {
        this.currentRate = new AtomicLong(initialRate);
    }

    public long get() {
        return currentRate.get();
    }

    public void set(long rate) {
        currentRate.set(rate);
    }
}
