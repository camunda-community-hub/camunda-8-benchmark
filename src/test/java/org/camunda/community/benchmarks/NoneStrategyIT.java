package org.camunda.community.benchmarks;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.metrics.MicrometerMetricsRecorder;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.camunda.community.benchmarks.decision.StartDecisionExecutor;
import org.camunda.community.benchmarks.flowcontrol.Bucket4jPiScheduler;
import org.camunda.community.benchmarks.flowcontrol.FlowControlInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the {@code none} strategy (fixed rate, no adjustment).
 * Verifies that the classic scheduler is active and no Bucket4j beans are created.
 */
@SpringBootTest(properties = {
    "benchmark.startRateAdjustmentStrategy=none",
    "benchmark.startPiPerSecond=10",
    "benchmark.startProcesses=true",
    "benchmark.startWorkers=false",
    "benchmark.startDecisions=false",
    "benchmark.autoDeployProcess=false",
    "benchmark.bpmnProcessId=none-strategy-test"
})
@CamundaSpringProcessTest
class NoneStrategyIT {

    private static final String TRIVIAL_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              targetNamespace="http://bpmn.io/schema/bpmn"
                              id="Definitions_1">
              <bpmn:process id="none-strategy-test" name="None Strategy Test" isExecutable="true">
                <bpmn:startEvent id="start"/>
                <bpmn:endEvent id="end"/>
                <bpmn:sequenceFlow id="flow" sourceRef="start" targetRef="end"/>
              </bpmn:process>
            </bpmn:definitions>
            """;

    @Autowired
    private CamundaClient client;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private BenchmarkConfiguration config;

    @Autowired
    private StatisticsCollector stats;

    @MockitoBean
    private CamundaClientConfiguration camundaClientConfiguration;

    @MockitoBean
    private StartPiExecutor startPiExecutor;

    @MockitoBean
    private StartDecisionExecutor startDecisionExecutor;

    @MockitoBean
    private MicrometerMetricsRecorder micrometerMetricsRecorder;

    @Test
    void classicSchedulerIsActive() {
        assertTrue(context.containsBean("startPiScheduler"),
                "StartPiScheduler should be active for 'none' strategy");
    }

    @Test
    void bucket4jBeansAreNotCreated() {
        assertFalse(context.containsBean("bucket4jPiScheduler"),
                "Bucket4jPiScheduler should NOT be active for 'none' strategy");
        assertFalse(context.containsBean("flowControlInterceptor"),
                "FlowControlInterceptor should NOT be active for 'none' strategy");
        assertFalse(context.containsBean("flowControlBucket"),
                "flowControlBucket should NOT be active for 'none' strategy");
    }

    @Test
    void deployAndStartProcessInstance() {
        client.newDeployResourceCommand()
                .addResourceStream(
                        new ByteArrayInputStream(TRIVIAL_BPMN.getBytes(StandardCharsets.UTF_8)),
                        "none-strategy-test.bpmn")
                .send()
                .join();

        var result = client.newCreateInstanceCommand()
                .bpmnProcessId("none-strategy-test")
                .latestVersion()
                .send()
                .join();

        assertTrue(result.getProcessInstanceKey() > 0,
                "Should successfully start a process instance with 'none' strategy");
    }
}
