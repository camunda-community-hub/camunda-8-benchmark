# Partition Pinning Feature

This document explains how to use the partition pinning feature to improve benchmark performance by having each client instance connect to specific partitions.

## Overview

By default, the benchmark client connects to all partitions across all brokers in the Camunda 8 cluster. The partition pinning feature allows each benchmark client to be "pinned" to a specific subset of partitions, ideally located on the same broker, which can significantly improve performance.

## How It Works

1. **Client Identity**: Each client has a unique numerical identity (client name from Kubernetes StatefulSet)
2. **Message-based Process Starting**: Instead of creating process instances directly, the client publishes messages with correlation keys that route to specific partitions
3. **Partition-specific Job Types**: Job workers only subscribe to job types that include their client name, ensuring they only handle jobs from their assigned partitions

## Configuration

### Required Properties

```properties
# Enable partition pinning
benchmark.client.enable-partition-pinning=true

# Total number of partitions in your Zeebe cluster
benchmark.client.partitionCount=9

# Total number of benchmark starters
benchmark.client.numberOfStarters=3
```

### BPMN Process Requirements

When using partition pinning, your BPMN process must have a message start event:

```xml
<bpmn:startEvent id="StartEvent_MessageStart" name="Start via Message">
  <bpmn:messageEventDefinition messageRef="Message_StartBenchmarkProcess" />
</bpmn:startEvent>
```

Use the provided `benchmark_partition_pinning.bpmn` process or add a message start event to your existing process.

### Kubernetes Configuration

Deploy as a StatefulSet to ensure stable pod identities:

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: camunda-8-benchmark-partitioned
spec:
  replicas: 3
  template:
    spec:
      containers:
        - name: camunda-8-benchmark
          env:
            - name: JAVA_OPTIONS
              value: >-
                -Dbenchmark.client.enable-partition-pinning=true
                -Dbenchmark.client.partitionCount=9
                -Dbenchmark.client.numberOfStarters=3
                -Dbenchmark.bpmnProcessId=benchmark_partition_pinning
                -Dbenchmark.bpmnResource=classpath:bpmn/benchmark_partition_pinning.bpmn
```

## Partition Assignment Strategy

The partition assignment follows this logic:

- **More partitions than starters**: Each client gets assigned to different partitions evenly distributed
- **More starters than partitions**: Multiple clients may share the same partition
- **Equal partitions and starters**: One-to-one mapping

Examples:
- 9 partitions, 3 starters: 
  - Client 0 → Partitions 0, 1, 2
  - Client 1 → Partitions 3, 4, 5  
  - Client 2 → Partitions 6, 7, 8
- 6 partitions, 2 starters:
  - Client 0 → Partitions 0, 1, 2
  - Client 1 → Partitions 3, 4, 5

## Job Type Naming

When partition pinning is enabled, job types are automatically suffixed with the client name:

- Normal: `benchmark-task-Task_1`
- With pinning: `benchmark-task-Task_1-0` (for client name 0)

This ensures that each client only processes jobs from its assigned partitions.

## Performance Benefits

- **Reduced network traffic**: Clients only communicate with their assigned brokers
- **Better cache locality**: Jobs and process instances are co-located on the same broker
- **Improved scalability**: Linear scaling with number of partitions and brokers
- **Reduced contention**: Each client works with a dedicated subset of data

## Monitoring

Monitor the following to verify partition pinning is working:

1. **Process Instance Distribution**: Check that process instances are evenly distributed across partitions
2. **Job Worker Activity**: Verify that each client only processes jobs with its client name suffix
3. **Broker Load**: Ensure load is distributed across brokers based on partition assignment

## Troubleshooting

### Common Issues

1. **Process instances not starting**: Ensure the BPMN process has a message start event with the correct message name (`StartBenchmarkProcess`)

2. **Jobs not being processed**: Verify job types in the BPMN process match the generated job types with client name suffixes

3. **Uneven distribution**: Check that partition count and replica count are configured correctly

### Logs to Check

```
INFO  - Partition pinning enabled: starterId=1, numericClientId=1, target-partitions=[3, 4, 5], partition-count=9, numberOfStarters=3
INFO  - Added job type 'benchmark-task-Task_1-1' to service task 'Task_1'
INFO  - Partition pinning enabled: registering workers for task type with client ID suffix: benchmark-task-1
```

## Migration from Regular Deployment

To migrate from a regular deployment to partition pinning:

1. Update your BPMN process to include a message start event (or use the provided template)
2. Change from Deployment to StatefulSet in Kubernetes
3. Add the partition pinning configuration properties
4. Deploy and verify that clients are processing jobs only from their assigned partitions