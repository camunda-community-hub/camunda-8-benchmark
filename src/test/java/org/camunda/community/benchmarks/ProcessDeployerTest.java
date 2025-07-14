package org.camunda.community.benchmarks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;

import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProcessDeployerTest {

    private ProcessDeployer processDeployer;
    private BenchmarkConfiguration config;

    @BeforeEach
    void setUp() {
        processDeployer = new ProcessDeployer();
        config = new BenchmarkConfiguration();
        
        // Use reflection to set the config field since it's @Autowired
        try {
            java.lang.reflect.Field configField = ProcessDeployer.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(processDeployer, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testInjectUniqueJobTypes_WithoutExistingJobTypes() throws Exception {
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

        // Use reflection to call the private method
        Method injectMethod = ProcessDeployer.class.getDeclaredMethod("injectUniqueJobTypes", String.class);
        injectMethod.setAccessible(true);
        String result = (String) injectMethod.invoke(processDeployer, bpmnWithoutJobTypes);

        // Verify zeebe namespace was added
        assertTrue(result.contains("xmlns:zeebe=\"http://camunda.org/schema/zeebe/1.0\""));
        
        // Verify extensionElements were added to both service tasks
        assertTrue(result.contains("<extensionElements"));
        
        // Verify zeebe:taskDefinition was added
        assertTrue(result.contains("<zeebe:taskDefinition"));
        assertTrue(result.contains("type=\"benchmark-task-Task_1\""));
        assertTrue(result.contains("type=\"benchmark-task-Task_2\""));
    }

    @Test
    void testInjectUniqueJobTypes_WithExistingJobTypes() throws Exception {
        String bpmnWithJobTypes = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" 
                              xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                              id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="test-process" isExecutable="true">
                <bpmn:serviceTask id="Task_1" name="Test Service Task">
                  <bpmn:extensionElements>
                    <zeebe:taskDefinition type="existing-job-type" />
                  </bpmn:extensionElements>
                  <bpmn:incoming>Flow_1</bpmn:incoming>
                  <bpmn:outgoing>Flow_2</bpmn:outgoing>
                </bpmn:serviceTask>
              </bpmn:process>
            </bpmn:definitions>
            """;

        // Use reflection to call the private method
        Method injectMethod = ProcessDeployer.class.getDeclaredMethod("injectUniqueJobTypes", String.class);
        injectMethod.setAccessible(true);
        String result = (String) injectMethod.invoke(processDeployer, bpmnWithJobTypes);

        // Verify the existing job type is preserved
        assertTrue(result.contains("type=\"existing-job-type\""));
        
        // Verify no new job types were added
        assertFalse(result.contains("benchmark-task-Task_1"));
    }

    @Test
    void testInjectUniqueJobTypes_MixedScenario() throws Exception {
        String bpmnMixed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" 
                              xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                              id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="test-process" isExecutable="true">
                <bpmn:serviceTask id="Task_1" name="Task with job type">
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

        // Use reflection to call the private method
        Method injectMethod = ProcessDeployer.class.getDeclaredMethod("injectUniqueJobTypes", String.class);
        injectMethod.setAccessible(true);
        String result = (String) injectMethod.invoke(processDeployer, bpmnMixed);

        // Verify the existing job type is preserved
        assertTrue(result.contains("type=\"existing-job-type\""));
        
        // Verify new job type was added only to Task_2
        assertTrue(result.contains("type=\"benchmark-task-Task_2\""));
        assertFalse(result.contains("benchmark-task-Task_1"));
    }

    @Test
    void testAdjustInputStreamBasedOnConfig_InjectsJobTypes() throws Exception {
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

        InputStream inputStream = new ByteArrayInputStream(bpmnWithoutJobTypes.getBytes());
        
        // Use reflection to call the private method
        Method adjustMethod = ProcessDeployer.class.getDeclaredMethod("adjustInputStreamBasedOnConfig", InputStream.class);
        adjustMethod.setAccessible(true);
        InputStream resultStream = (InputStream) adjustMethod.invoke(processDeployer, inputStream);
        
        String result = new String(resultStream.readAllBytes());

        // Verify zeebe namespace and job type were added
        assertTrue(result.contains("xmlns:zeebe=\"http://camunda.org/schema/zeebe/1.0\""));
        assertTrue(result.contains("type=\"benchmark-task-Task_1\""));
    }
}