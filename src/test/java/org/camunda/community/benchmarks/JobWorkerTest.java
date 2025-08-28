/*
 * Copyright 2024 Camunda Services GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.community.benchmarks;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.actuator.MicrometerMetricsRecorder;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.TaskScheduler;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobWorkerTest {

    @Mock
    private ZeebeClient client;

    @Mock
    private StatisticsCollector stats;

    @Mock
    private TaskScheduler scheduler;

    @Mock
    private BenchmarkCompleteJobExceptionHandlingStrategy exceptionHandlingStrategy;

    @Mock
    private MicrometerMetricsRecorder micrometerMetricsRecorder;

    @Spy
    private BenchmarkConfiguration config;

    private JobWorker jobWorker;

    private List<String> registeredJobTypes;

    @BeforeEach
    void setUp() throws Exception {
        // Create a partial mock of JobWorker to spy on registerWorker calls
        jobWorker = spy(new JobWorker());
        
        // Use reflection to set private fields
        setField(jobWorker, "config", config);
        setField(jobWorker, "client", client);
        setField(jobWorker, "stats", stats);
        setField(jobWorker, "scheduler", scheduler);
        setField(jobWorker, "exceptionHandlingStrategy", exceptionHandlingStrategy);
        setField(jobWorker, "micrometerMetricsRecorder", micrometerMetricsRecorder);

        // Track registered job types
        registeredJobTypes = new ArrayList<>();
        
        // Mock the registerWorker method to capture calls - use lenient to avoid unnecessary stubbing warnings
        lenient().doAnswer(invocation -> {
            String jobType = invocation.getArgument(0);
            Boolean markCompleted = invocation.getArgument(1);
            registeredJobTypes.add(jobType + (markCompleted ? ":completed" : ":normal"));
            return null;
        }).when(jobWorker).registerWorker(anyString(), any(Boolean.class));

        // Setup default configuration
        config.setStartWorkers(true);
        config.setAutoDeployProcess(true);
        config.setJobType("benchmark-task");
        config.setMultipleJobTypes(3);
        config.setStarterId("starter1");
        config.setEnablePartitionPinning(false);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void testStartWorkers_WithWorkersDisabled_ShouldNotRegisterAnyWorkers() {
        // Given
        config.setStartWorkers(false);

        // When
        jobWorker.startWorkers();

        // Then
        verify(jobWorker, never()).registerWorker(anyString(), any(Boolean.class));
        assertTrue(registeredJobTypes.isEmpty());
    }

    @Test
    void testStartWorkers_WithMultipleJobTypesFromConfig_ShouldRegisterConfigWorkers() {
        // Given
        config.setAutoDeployProcess(false); // Disable BPMN processing to test config only
        config.setJobType("benchmark-task");
        config.setMultipleJobTypes(2);
        config.setStarterId("starter1");

        // When
        jobWorker.startWorkers();

        // Then
        // With 2 config job types, they get simple registerWorker treatment (not variants)
        // Should register workers for benchmark-task-1 and benchmark-task-2 as simple workers = 2
        verify(jobWorker, times(2)).registerWorker(anyString(), any(Boolean.class));
        
        // Verify the specific job types registered (simple registration, no variants)
        assertTrue(registeredJobTypes.contains("benchmark-task-1:normal"));
        assertTrue(registeredJobTypes.contains("benchmark-task-2:completed"));
        
        // Verify that variants are NOT registered when there are multiple config job types
        assertFalse(registeredJobTypes.contains("benchmark-task-1-starter1:normal"));
        assertFalse(registeredJobTypes.contains("benchmark-task-1-completed:completed"));
        assertFalse(registeredJobTypes.contains("benchmark-task-2-starter1:normal"));
        assertFalse(registeredJobTypes.contains("benchmark-task-2-completed:completed"));
        
        assertEquals(2, registeredJobTypes.size());
    }

    @Test
    void testStartWorkers_WithSingleJobTypeFromConfig_ShouldRegisterSingleWorker() {
        // Given
        config.setAutoDeployProcess(false); // Disable BPMN processing
        config.setJobType("custom-task");
        config.setMultipleJobTypes(0); // Single job type mode
        config.setStarterId("starter1");

        // When
        jobWorker.startWorkers();

        // Then
        // Should register workers for custom-task with 4 variants
        verify(jobWorker, times(4)).registerWorker(anyString(), any(Boolean.class));
        
        assertTrue(registeredJobTypes.contains("custom-task:normal"));
        assertTrue(registeredJobTypes.contains("custom-task-starter1:normal"));
        assertTrue(registeredJobTypes.contains("custom-task-completed:completed"));
        assertTrue(registeredJobTypes.contains("custom-task-starter1-completed:completed"));
    }

    @Test
    void testStartWorkers_WithCommaSeparatedJobTypes_ShouldRegisterAllTypes() {
        // Given
        config.setAutoDeployProcess(false); // Disable BPMN processing
        config.setJobType("task-a,task-b,task-c");
        config.setStarterId("starter1");

        // When
        jobWorker.startWorkers();

        // Then
        // With 3 config job types, they get simple registerWorker treatment (not variants)
        // Should register workers for 3 job types as simple workers = 3 total
        verify(jobWorker, times(3)).registerWorker(anyString(), any(Boolean.class));
        
        // Verify each task type is registered as simple worker (no variants)
        assertTrue(registeredJobTypes.contains("task-a:normal"));
        assertTrue(registeredJobTypes.contains("task-b:normal"));
        assertTrue(registeredJobTypes.contains("task-c:normal"));
        
        // Verify that variants are NOT registered when there are multiple config job types
        assertFalse(registeredJobTypes.contains("task-a-starter1:normal"));
        assertFalse(registeredJobTypes.contains("task-a-completed:completed"));
        
        assertEquals(3, registeredJobTypes.size());
    }

    @Test
    void testStartWorkers_WithBpmnResource_ShouldRegisterBpmnJobTypes() {
        // Given
        config.setAutoDeployProcess(true);
        config.setBpmnResource(new ClassPathResource[] {
            new ClassPathResource("bpmn/typical_process_10_jobtypes.bpmn")
        });
        config.setJobType("benchmark-task");
        config.setMultipleJobTypes(2); // This will create benchmark-task-1, benchmark-task-2
        config.setStarterId("starter1");

        // When
        jobWorker.startWorkers();

        // Debug: Print all registered job types
        System.out.println("All registered job types: " + registeredJobTypes);

        // Then
        // With 2 config job types, they get simple registerWorker treatment (not variants)
        // - 2 config job types (benchmark-task-1, benchmark-task-2) as simple workers = 2
        // - 8 BPMN job types that are NOT in config (benchmark-task-3 through benchmark-task-10) = 8
        // Total = 10 workers
        verify(jobWorker, times(10)).registerWorker(anyString(), any(Boolean.class));
        
        // Verify config-based job types are registered as simple workers (no variants)
        assertTrue(registeredJobTypes.contains("benchmark-task-1:normal"));
        assertTrue(registeredJobTypes.contains("benchmark-task-2:completed"));
        
        // Verify BPMN-only job types are registered as normal workers
        assertTrue(registeredJobTypes.contains("benchmark-task-3:normal"));
        assertTrue(registeredJobTypes.contains("benchmark-task-4:normal"));
        assertTrue(registeredJobTypes.contains("benchmark-task-10:normal"));
        
        // Verify that config job types do NOT get variant treatment when there are multiple
        assertFalse(registeredJobTypes.contains("benchmark-task-1-starter1:normal"));
        assertFalse(registeredJobTypes.contains("benchmark-task-1-completed:completed"));
        
        // Total should be exactly 10 registrations
        assertEquals(10, registeredJobTypes.size());
        
        // Verify each job type appears exactly once
        long benchmark1Count = registeredJobTypes.stream().filter(type -> type.equals("benchmark-task-1:normal")).count();
        assertEquals(1, benchmark1Count); // Should be exactly 1 (simple registration, no variants)
        
        long benchmark2Count = registeredJobTypes.stream().filter(type -> type.equals("benchmark-task-2:completed")).count();
        assertEquals(1, benchmark2Count); // Should be exactly 1 (simple registration, no variants)
    }

    @Test
    void testStartWorkers_WithPartitionPinning_ShouldAddStarterIdPrefix() {
        // Given
        config.setAutoDeployProcess(false);
        config.setBpmnResource(null);
        config.setJobType("benchmark-task");
        config.setMultipleJobTypes(1);
        config.setStarterId("benchmark-0");
        config.setEnablePartitionPinning(true);

        // When
        jobWorker.startWorkers();

        // Then
        verify(jobWorker, times(1)).registerWorker(anyString(), any(Boolean.class));
        
        // Verify config-based worker with partition pinning prefix
        
        assertEquals("benchmark-0-benchmark-task-1:completed", registeredJobTypes.get(0));
    }

    @Test
    void testStartWorkers_WithPartitionPinningButNoStarterId_ShouldNotAddPrefix() {
        // Given
        config.setAutoDeployProcess(true);
        config.setBpmnResource(new ClassPathResource[] {
            new ClassPathResource("bpmn/complex_subprocess.bpmn")
        });
        config.setJobType("benchmark-task");
        config.setMultipleJobTypes(0);
        config.setStarterId(""); // Empty starter ID
        config.setEnablePartitionPinning(true);

        // When
        jobWorker.startWorkers();

        // Then
        // Should register workers for:
        // - 1 config job type (benchmark-task) with 4 variants = 4
        // Total = 4 workers
        verify(jobWorker, times(2)).registerWorker(anyString(), any(Boolean.class));
        
        // Verify BPMN-only worker without prefix (due to empty starter ID)
        assertEquals("benchmark-task:normal", registeredJobTypes.get(0));
        assertEquals("benchmark-task-completed:completed", registeredJobTypes.get(1));

    }

    @Test
    void testStartWorkers_WithNoBpmnResource_ShouldOnlyRegisterConfigWorkers() {
        // Given
        config.setAutoDeployProcess(false);
        config.setBpmnResource(null);
        config.setJobType("benchmark-task");
        config.setMultipleJobTypes(0);
        config.setStarterId("starter1");

        // When
        jobWorker.startWorkers();

        // Then
        // Should only register config workers
        verify(jobWorker, times(4)).registerWorker(anyString(), any(Boolean.class));
        
        assertTrue(registeredJobTypes.contains("benchmark-task:normal"));
        assertTrue(registeredJobTypes.contains("benchmark-task-starter1:normal"));
        assertTrue(registeredJobTypes.contains("benchmark-task-completed:completed"));
        assertTrue(registeredJobTypes.contains("benchmark-task-starter1-completed:completed"));
    }

    @Test
    void testStartWorkers_WithEmptyBpmnResource_ShouldOnlyRegisterConfigWorkers() {
        // Given
        config.setAutoDeployProcess(true);
        config.setBpmnResource(new ClassPathResource[0]); // Empty array
        config.setJobType("benchmark-task");
        config.setMultipleJobTypes(1);

        // When
        jobWorker.startWorkers();

        // Then
        // Should only register config workers
        verify(jobWorker, times(1)).registerWorker(anyString(), any(Boolean.class));
        
        assertEquals("benchmark-task-1:completed", registeredJobTypes.get(0));
    }
}
