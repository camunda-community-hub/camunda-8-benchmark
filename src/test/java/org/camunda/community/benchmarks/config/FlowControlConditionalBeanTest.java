package org.camunda.community.benchmarks.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code @ConditionalOnExpression} annotations correctly
 * activate/deactivate beans based on {@code benchmark.startRateAdjustmentStrategy}.
 * <p>
 * Uses lightweight {@link ApplicationContextRunner} — no full Spring Boot context.
 */
class FlowControlConditionalBeanTest {

    /**
     * Minimal config that mimics the conditional on StartPiScheduler.
     * We can't use the real StartPiScheduler (extends BenchmarkScheduler, needs deep deps),
     * so we test the same conditional expressions on a lightweight stand-in.
     */
    @Configuration
    @ConditionalOnProperty(name = "benchmark.startProcesses", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression(
        "'${benchmark.startRateAdjustmentStrategy:backpressure}' != 'backoff' and "
      + "'${benchmark.startRateAdjustmentStrategy:backpressure}' != 'autoTune'")
    static class ClassicSchedulerStandIn {
    }

    @Configuration
    @ConditionalOnExpression(
        "'${benchmark.startRateAdjustmentStrategy:backpressure}' == 'backoff' or "
      + "'${benchmark.startRateAdjustmentStrategy:backpressure}' == 'autoTune'")
    static class FlowControlStandIn {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClassicSchedulerStandIn.class, FlowControlStandIn.class);

    @Test
    void backoffStrategy_activatesFlowControlBeans() {
        runner.withPropertyValues("benchmark.startRateAdjustmentStrategy=backoff")
                .run(context -> {
                    assertThat(context).hasSingleBean(FlowControlStandIn.class);
                    assertThat(context).doesNotHaveBean(ClassicSchedulerStandIn.class);
                });
    }

    @Test
    void autoTuneStrategy_activatesFlowControlBeans() {
        runner.withPropertyValues("benchmark.startRateAdjustmentStrategy=autoTune")
                .run(context -> {
                    assertThat(context).hasSingleBean(FlowControlStandIn.class);
                    assertThat(context).doesNotHaveBean(ClassicSchedulerStandIn.class);
                });
    }

    @Test
    void backpressureStrategy_activatesClassicScheduler() {
        runner.withPropertyValues("benchmark.startRateAdjustmentStrategy=backpressure")
                .run(context -> {
                    assertThat(context).hasSingleBean(ClassicSchedulerStandIn.class);
                    assertThat(context).doesNotHaveBean(FlowControlStandIn.class);
                });
    }

    @Test
    void defaultStrategy_activatesClassicScheduler() {
        // No property set — defaults to 'backpressure' via SpEL :backpressure default
        runner.run(context -> {
            assertThat(context).hasSingleBean(ClassicSchedulerStandIn.class);
            assertThat(context).doesNotHaveBean(FlowControlStandIn.class);
        });
    }
}
