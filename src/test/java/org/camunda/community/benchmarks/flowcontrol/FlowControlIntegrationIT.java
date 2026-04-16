package org.camunda.community.benchmarks.flowcontrol;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.metrics.MicrometerMetricsRecorder;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.camunda.community.benchmarks.StartPiExecutor;
import org.camunda.community.benchmarks.decision.StartDecisionExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests verifying Bucket4j flow control with a real Zeebe engine
 * via Camunda Process Test (testcontainers).
 * <p>
 * Requires Docker to be running.
 * <p>
 * We mock {@link StartPiExecutor} and {@link StartDecisionExecutor} because their
 * {@code @PostConstruct} methods have heavy dependencies (payload files, JSON mapper)
 * that aren't relevant to flow control testing.
 * <p>
 * The default test client (injected by {@code @CamundaSpringProcessTest}) does NOT
 * include Spring-registered {@code ClientInterceptor} beans — it bypasses
 * {@code CamundaClientProdAutoConfiguration}. To test interceptor wiring, we create
 * a custom client via {@link CamundaProcessTestContext#createClient} with the
 * interceptor explicitly attached.
 */
@SpringBootTest(properties = {
    "benchmark.startRateAdjustmentStrategy=autoTune",
    "benchmark.startPiPerSecond=10",
    "benchmark.startPiReduceFactor=0.1",
    "benchmark.startPiIncreaseFactor=0.4",
    "benchmark.maxBackpressurePercentage=10",
    "benchmark.startProcesses=true",
    "benchmark.startWorkers=false",
    "benchmark.startDecisions=false",
    "benchmark.autoDeployProcess=false",
    "benchmark.bpmnProcessId=flow-control-test"
})
@CamundaSpringProcessTest
class FlowControlIntegrationIT {

    private static final String TRIVIAL_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              targetNamespace="http://bpmn.io/schema/bpmn"
                              id="Definitions_1">
              <bpmn:process id="flow-control-test" name="Flow Control Test" isExecutable="true">
                <bpmn:startEvent id="start"/>
                <bpmn:endEvent id="end"/>
                <bpmn:sequenceFlow id="flow" sourceRef="start" targetRef="end"/>
              </bpmn:process>
            </bpmn:definitions>
            """;

    @Autowired
    private CamundaClient client;

    @Autowired
    private CamundaProcessTestContext processTestContext;

    @MockitoBean
    private CamundaClientConfiguration camundaClientConfiguration;

    @MockitoBean
    private StartPiExecutor startPiExecutor;

    @MockitoBean
    private StartDecisionExecutor startDecisionExecutor;

    @MockitoBean
    private MicrometerMetricsRecorder micrometerMetricsRecorder;

    @Autowired
    private FlowControlInterceptor flowControlInterceptor;

    @Autowired
    private Bucket4jPiScheduler bucket4jPiScheduler;

    @Autowired
    private MeterRegistry meterRegistry;

    private void deployProcess() {
        client.newDeployResourceCommand()
                .addResourceStream(
                        new ByteArrayInputStream(TRIVIAL_BPMN.getBytes(StandardCharsets.UTF_8)),
                        "flow-control-test.bpmn")
                .send()
                .join();
    }

    @Test
    void interceptorIsWiredIntoCamundaClient() {
        deployProcess();

        // The default test client doesn't pick up @Bean interceptors.
        // Create a custom client with the interceptor explicitly attached.
        try (CamundaClient customClient = processTestContext.createClient(
                builder -> builder.withInterceptors(flowControlInterceptor))) {

            customClient.newCreateInstanceCommand()
                    .bpmnProcessId("flow-control-test")
                    .latestVersion()
                    .send()
                    .join();
        }

        // The interceptor should have seen the gRPC call
        assertTrue(flowControlInterceptor.getTotalCallCount().get() > 0,
                "FlowControlInterceptor should intercept gRPC calls when wired into the client");
    }

    @Test
    void gaugesAreRegistered() {
        assertNotNull(meterRegistry.find("pi_rate_goal").gauge(),
                "pi_rate_goal gauge should be registered");
        assertTrue(meterRegistry.get("pi_rate_goal").gauge().value() > 0,
                "pi_rate_goal should reflect the initial rate");

        assertNotNull(meterRegistry.find("flowcontrol_available_tokens").gauge(),
                "flowcontrol_available_tokens gauge should be registered");
    }

    @Test
    void autoTuneStrategy_adjustsRateUpward() {
        deployProcess();

        double initialRate = meterRegistry.get("pi_rate_goal").gauge().value();

        // Simulate traffic with no backpressure — manually trigger adjustRate
        // instead of waiting for the @Scheduled timer
        flowControlInterceptor.getTotalCallCount().set(100);
        flowControlInterceptor.getBackpressureCount().set(0);

        bucket4jPiScheduler.adjustRate();

        double adjustedRate = meterRegistry.get("pi_rate_goal").gauge().value();

        // With 0% backpressure (below 10% threshold), rate should increase by 40%
        assertTrue(adjustedRate > initialRate,
                "autoTune should increase rate when backpressure is low: initial=" +
                        initialRate + ", adjusted=" + adjustedRate);
    }
}
