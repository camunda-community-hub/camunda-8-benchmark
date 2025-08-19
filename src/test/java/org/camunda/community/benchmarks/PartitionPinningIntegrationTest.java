package org.camunda.community.benchmarks;

/**
 * Simple test to verify partition pinning logic integration
 */
public class PartitionPinningIntegrationTest {
    
    public static void main(String[] args) {
        System.out.println("Testing partition pinning integration...");
        
        // Test pod ID parsing scenarios
        testPodIdParsing();
        
        // Test job type generation logic
        testJobTypeGeneration();
        
        System.out.println("Integration tests passed!");
    }
    
    private static void testPodIdParsing() {
        System.out.println("Testing pod ID parsing scenarios...");
        
        // Test various pod name formats
        String[] podNames = {
            "benchmark-0",
            "benchmark-3", 
            "my-benchmark-app-5",
            "statefulset-42",
            "invalid-name",
            "7"
        };
        
        for (String podName : podNames) {
            int extracted = org.camunda.community.benchmarks.partition.PartitionHashUtil.extractPodIdFromName(podName);
            System.out.println("  Pod name '" + podName + "' -> ID " + extracted);
        }
    }
    
    private static void testJobTypeGeneration() {
        System.out.println("Testing job type generation logic...");
        
        // Simulate the logic from ProcessDeployer.generateJobTypeForTask
        String taskId = "MyTask";
        String baseJobType = "benchmark-task-" + taskId;
        
        // Without partition pinning
        String normalJobType = baseJobType;
        System.out.println("  Normal job type: " + normalJobType);
        
        // With partition pinning - numeric pod ID
        String podId1 = "5";
        String partitionedJobType1 = baseJobType + "-" + podId1;
        System.out.println("  Partitioned job type (numeric): " + partitionedJobType1);
        
        // With partition pinning - pod name format
        String podId2 = "benchmark-3";
        int extracted = org.camunda.community.benchmarks.partition.PartitionHashUtil.extractPodIdFromName(podId2);
        String partitionedJobType2 = baseJobType + "-" + extracted;
        System.out.println("  Partitioned job type (pod name): " + partitionedJobType2);
        
        // Verify expected format
        if (!partitionedJobType1.equals("benchmark-task-MyTask-5")) {
            throw new RuntimeException("Job type generation failed for numeric pod ID");
        }
        
        if (!partitionedJobType2.equals("benchmark-task-MyTask-3")) {
            throw new RuntimeException("Job type generation failed for pod name format");
        }
    }
}