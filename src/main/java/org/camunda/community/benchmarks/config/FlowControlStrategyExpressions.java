package org.camunda.community.benchmarks.config;

/**
 * Shared SpEL condition strings used to activate either the classic schedulers
 * ({@code StartPiScheduler}, {@code StartDecisionScheduler}) or the Bucket4j-based
 * flow control beans, depending on {@code benchmark.startRateAdjustmentStrategy}.
 * <p>
 * Kept in one place so the two conditions can't drift out of sync as strategies are added.
 */
public final class FlowControlStrategyExpressions {

    public static final String IS_FLOW_CONTROL_STRATEGY =
        "'${benchmark.startRateAdjustmentStrategy:backpressure}' == 'backoff' or "
      + "'${benchmark.startRateAdjustmentStrategy:backpressure}' == 'autoTune' or "
      + "'${benchmark.startRateAdjustmentStrategy:backpressure}' == 'autoTuneJobRatio'";

    public static final String IS_NOT_FLOW_CONTROL_STRATEGY =
        "'${benchmark.startRateAdjustmentStrategy:backpressure}' != 'backoff' and "
      + "'${benchmark.startRateAdjustmentStrategy:backpressure}' != 'autoTune' and "
      + "'${benchmark.startRateAdjustmentStrategy:backpressure}' != 'autoTuneJobRatio'";

    private FlowControlStrategyExpressions() {
    }
}
