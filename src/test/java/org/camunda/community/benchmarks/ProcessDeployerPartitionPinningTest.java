package org.camunda.community.benchmarks;

import static org.junit.jupiter.api.Assertions.*;

import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProcessDeployerPartitionPinningTest {

    private ProcessDeployer processDeployer;
    private BenchmarkConfiguration config;

    @BeforeEach
    void setUp() {
        config = new BenchmarkConfiguration();
        processDeployer = new ProcessDeployer(null, config); // zeebeClient not needed for testing
    }

    @Test
    void testInjectUniqueJobTypes_WithPartitionPinning() throws Exception {
        // Configure partition pinning
        config.setEnablePartitionPinning(true);
        config.setStarterId("benchmark-2");
        
        String bpmnWithoutJobTypes = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" 
                              xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" 
                              id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="test-process" isExecutable="true">
                <bpmn:serviceTask id="Task_1" name="Test Service Task">
                  <bpmn:incoming>Flow_1</bpmn:incoming>
                  <bpmn:outgoing>Flow_2</bpmn:outgoing>
                </bpmn:serviceTask>
                <bpmn:serviceTask id="Task_2" name="Another Service Task">
                  <bpmn:incoming>Flow_2</bpmn:incoming>
                  <bpmn:outgoing>Flow_3</bpmn:outgoing>
                </bpmn:serviceTask>
              </bpmn:process>
            </bpmn:definitions>
            """;

        String result = processDeployer.injectUniqueJobTypes(bpmnWithoutJobTypes);

        // Verify zeebe namespace was added
        assertTrue(result.contains("xmlns:zeebe=\"http://camunda.org/schema/zeebe/1.0\""));
        
        // Verify job types with Zeebe expressions were added
        assertTrue(result.contains("= benchmark_starter_id + &quot;-benchmark-task-Task_1&quot;"));
        assertTrue(result.contains("= benchmark_starter_id + &quot;-benchmark-task-Task_2&quot;"));
        
        // Verify extensionElements were added
        assertTrue(result.contains("<extensionElements"));
        assertTrue(result.contains("zeebe:taskDefinition"));
    }

    @Test
    void testInjectUniqueJobTypes_WithNumericStarterId() throws Exception {
        // Configure partition pinning with numeric starter ID
        config.setEnablePartitionPinning(true);
        config.setStarterId("5");
        
        String bpmnWithoutJobTypes = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" 
                              id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="test-process" isExecutable="true">
                <bpmn:serviceTask id="MyTask" name="My Service Task">
                  <bpmn:incoming>Flow_1</bpmn:incoming>
                  <bpmn:outgoing>Flow_2</bpmn:outgoing>
                </bpmn:serviceTask>
              </bpmn:process>
            </bpmn:definitions>
            """;

        String result = processDeployer.injectUniqueJobTypes(bpmnWithoutJobTypes);

        // Verify job type with Zeebe expression was added
        assertTrue(result.contains("= benchmark_starter_id + &quot;-benchmark-task-MyTask&quot;"));
    }

    @Test
    void testInjectUniqueJobTypes_WithoutPartitionPinning() throws Exception {
        // Partition pinning disabled (default)
        config.setEnablePartitionPinning(false);
        
        String bpmnWithoutJobTypes = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" 
                              id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="test-process" isExecutable="true">
                <bpmn:serviceTask id="Task_1" name="Test Service Task">
                  <bpmn:incoming>Flow_1</bpmn:incoming>
                  <bpmn:outgoing>Flow_2</bpmn:outgoing>
                </bpmn:serviceTask>
              </bpmn:process>
            </bpmn:definitions>
            """;

        String result = processDeployer.injectUniqueJobTypes(bpmnWithoutJobTypes);

        // Verify normal job type without client name suffix
        assertTrue(result.contains("benchmark-task-Task_1"));
        assertFalse(result.contains("-benchmark-task-Task_1"));
    }

    @Test
    void testInjectUniqueJobTypes_PartitionPinningEnabledButNoStarterId() throws Exception {
        // Partition pinning enabled but using default starter ID
        config.setEnablePartitionPinning(true);
        // Don't set starterId - should use default "benchmarkStarter1"
        
        String bpmnWithoutJobTypes = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" 
                              id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="test-process" isExecutable="true">
                <bpmn:serviceTask id="Task_1" name="Test Service Task">
                  <bpmn:incoming>Flow_1</bpmn:incoming>
                  <bpmn:outgoing>Flow_2</bpmn:outgoing>
                </bpmn:serviceTask>
              </bpmn:process>
            </bpmn:definitions>
            """;

        String result = processDeployer.injectUniqueJobTypes(bpmnWithoutJobTypes);

        // Should use default starterId and Zeebe expression
        assertTrue(result.contains("= benchmark_starter_id + &quot;-benchmark-task-Task_1&quot;"));
    }

    @Test
    void testInjectUniqueJobTypes_WithExistingJobTypesAndPartitionPinning() throws Exception {
        // Configure partition pinning
        config.setEnablePartitionPinning(true);
        config.setStarterId("1");
        
        String bpmnWithJobTypes = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" 
                              xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                              id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="test-process" isExecutable="true">
                <bpmn:serviceTask id="Task_1" name="Task with existing job type">
                  <bpmn:extensionElements>
                    <zeebe:taskDefinition type="existing-job-type" />
                  </bpmn:extensionElements>
                  <bpmn:incoming>Flow_1</bpmn:incoming>
                  <bpmn:outgoing>Flow_2</bpmn:outgoing>
                </bpmn:serviceTask>
                <bpmn:serviceTask id="Task_2" name="Task without job type">
                  <bpmn:incoming>Flow_2</bpmn:incoming>
                  <bpmn:outgoing>Flow_3</bpmn:outgoing>
                </bpmn:serviceTask>
              </bpmn:process>
            </bpmn:definitions>
            """;

        String result = processDeployer.injectUniqueJobTypes(bpmnWithJobTypes);

        // Verify the existing job type is preserved
        assertTrue(result.contains("type=\"existing-job-type\""));
        
        // Verify new job type with Zeebe expression was added only to Task_2
        assertTrue(result.contains("= benchmark_starter_id + &quot;-benchmark-task-Task_2&quot;"));
        // Should not have modified Task_1
        assertFalse(result.contains("benchmark-task-Task_1"));
    }
}