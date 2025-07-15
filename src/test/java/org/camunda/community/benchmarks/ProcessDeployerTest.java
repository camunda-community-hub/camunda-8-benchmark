package org.camunda.community.benchmarks;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        
        // Use reflection to set the private config field
        try {
            var configField = ProcessDeployer.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(processDeployer, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testJobTypeReplacement() throws Exception {
        // Setup
        String originalBpmn = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                             targetNamespace="http://camunda.org/schema/1.0/bpmn">
              <bpmn:process id="test-process" isExecutable="true">
                <bpmn:serviceTask id="task1">
                  <bpmn:extensionElements>
                    <zeebe:taskDefinition type="old-job-type" />
                  </bpmn:extensionElements>
                </bpmn:serviceTask>
              </bpmn:process>
            </bpmn:definitions>
            """;

        config.setJobTypesToReplace("old-job-type");
        config.setJobType("new-job-type");

        // Execute
        InputStream inputStream = new ByteArrayInputStream(originalBpmn.getBytes());
        Method adjustMethod = ProcessDeployer.class.getDeclaredMethod("adjustInputStreamBasedOnConfig", InputStream.class);
        adjustMethod.setAccessible(true);
        InputStream result = (InputStream) adjustMethod.invoke(processDeployer, inputStream);

        // Verify
        String modifiedBpmn = new String(result.readAllBytes());
        assertTrue(modifiedBpmn.contains("new-job-type"));
        assertTrue(!modifiedBpmn.contains("old-job-type"));
    }

    @Test
    void testServiceTaskBuilderApiUsed() throws Exception {
        // Setup - valid BPMN that should be parsed by BPMN Model API
        String originalBpmn = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                             targetNamespace="http://camunda.org/schema/1.0/bpmn">
              <bpmn:process id="test-process" isExecutable="true">
                <bpmn:serviceTask id="task1">
                  <bpmn:extensionElements>
                    <zeebe:taskDefinition type="old-task-type" />
                  </bpmn:extensionElements>
                </bpmn:serviceTask>
                <bpmn:serviceTask id="task2">
                  <bpmn:extensionElements>
                    <zeebe:taskDefinition type="another-old-type" />
                  </bpmn:extensionElements>
                </bpmn:serviceTask>
              </bpmn:process>
            </bpmn:definitions>
            """;

        config.setJobTypesToReplace("old-task-type,another-old-type");
        config.setJobType("unified-job-type");

        // Execute
        InputStream inputStream = new ByteArrayInputStream(originalBpmn.getBytes());
        Method adjustMethod = ProcessDeployer.class.getDeclaredMethod("adjustInputStreamBasedOnConfig", InputStream.class);
        adjustMethod.setAccessible(true);
        InputStream result = (InputStream) adjustMethod.invoke(processDeployer, inputStream);

        // Verify
        String modifiedBpmn = new String(result.readAllBytes());
        
        // Both service tasks should have the new job type
        assertTrue(modifiedBpmn.contains("unified-job-type"));
        // Old job types should be replaced
        assertTrue(!modifiedBpmn.contains("old-task-type"));
        assertTrue(!modifiedBpmn.contains("another-old-type"));
        
        // Verify it's using proper BPMN structure (not string replacement artifacts)
        assertTrue(modifiedBpmn.contains("<zeebe:taskDefinition type=\"unified-job-type\""));
    }

    @Test
    void testNoReplacementWhenConfigIsNull() throws Exception {
        // Setup
        String originalBpmn = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions>
              <bpmn:process id="test-process">
                <bpmn:serviceTask id="task1" />
              </bpmn:process>
            </bpmn:definitions>
            """;

        // Leave config with null values (default)

        // Execute
        InputStream inputStream = new ByteArrayInputStream(originalBpmn.getBytes());
        Method adjustMethod = ProcessDeployer.class.getDeclaredMethod("adjustInputStreamBasedOnConfig", InputStream.class);
        adjustMethod.setAccessible(true);
        InputStream result = (InputStream) adjustMethod.invoke(processDeployer, inputStream);

        // Verify - should return the same stream
        assertNotNull(result);
        String resultContent = new String(result.readAllBytes());
        assertTrue(resultContent.contains("test-process"));
    }
}