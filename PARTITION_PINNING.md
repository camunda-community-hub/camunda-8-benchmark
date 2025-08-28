# Partition Pinning Feature

This document explains how to use the partition pinning feature to improve benchmark performance by having each starter instance connect to specific partitions.

## Overview

By default, the benchmark starter connects to all partitions across all brokers in the Camunda 8 cluster. The partition pinning feature allows each benchmark starter to be "pinned" to a specific subset of partitions, ideally located on the same broker, which can significantly improve performance.

## How It Works

1. **Starter Identity**: Each starter has a unique identity (starterId configuration)
2. **Message-based Process Starting**: Instead of creating process instances directly, the starter publishes messages with correlation keys that route to specific partitions
3. **Partition-specific Job Types**: Job workers only subscribe to job types that include their starter ID, ensuring they only handle jobs from their assigned partitions

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

Use the provided `seq-2tasks-msg-start-no-job-types.bpmn` process or add a message start event to your existing process.

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
                -Dbenchmark.bpmnProcessId=seq-2tasks-msg-start-no-job-types
                -Dbenchmark.bpmnResource=classpath:bpmn/seq-2tasks-msg-start-no-job-types.bpmn
```

## Partition Assignment Strategy

The partition assignment follows this logic:

- **More partitions than starters**: Each starter gets assigned to different partitions evenly distributed
- **More starters than partitions**: Extra starters may not get partitions assigned
- **Equal partitions and starters**: One-to-one mapping

Examples:
- 9 partitions, 3 starters: 
  - Starter 0 → Partitions 1, 4, 7
  - Starter 1 → Partitions 2, 5, 8  
  - Starter 2 → Partitions 3, 6, 9
- 6 partitions, 2 starters:
  - Starter 0 → Partitions 1, 3, 5
  - Starter 1 → Partitions 2, 4, 6
- 3 partitions, 5 starters:
  - Starter 0 → Partition 1
  - Starter 1 → Partition 2
  - Starter 2 → Partition 3
  - Starter 3 → Partition 1
  - Starter 4 → Partition 2

## Job Type Naming

When partition pinning is enabled, job types are automatically prefixed with the starter ID:

- Normal: `benchmark-task-Task_1`
- With pinning: `starter-0-benchmark-task-Task_1` (for starter ID `starter-0`)

This ensures that each starter only processes jobs from its assigned partitions.

## Performance Benefits

- **Reduced network traffic**: Starters only communicate with their assigned brokers
- **Improved scalability**: Linear scaling with number of partitions and brokers
- **Reduced contention**: Each starter works with a dedicated subset of data

## Monitoring

Monitor the following to verify partition pinning is working:

1. **Process Instance Distribution**: Check that process instances are evenly distributed across partitions
2. **Job Worker Activity**: Verify that each starter only processes jobs with its starter ID prefix
3. **Broker Load**: Ensure load is distributed across brokers based on partition assignment

## Troubleshooting

### Common Issues

1. **Process instances not starting**: Ensure the BPMN process has a message start event with the correct message name (`StartBenchmarkProcess`)

2. **Jobs not being processed**: Verify job types in the BPMN process match the generated job types with starter ID prefixes

3. **Uneven distribution**: Check that partition count and numberOfStarters are configured correctly

### Logs to Check

```
INFO  - Partition pinning enabled: starterId=starter-1, target-partitions=[3, 4, 5], partition-count=9, numberOfStarters=3
INFO  - Added job type 'starter-1-benchmark-task-Task_1' to service task 'Task_1'
INFO  - Partition pinning enabled: registering workers for task type with starter ID prefix: starter-1-benchmark-task
```

## Migration from Regular Deployment

To migrate from a regular deployment to partition pinning:

1. Update your BPMN process to include a message start event (or use the provided template)
2. Configure the partition pinning properties with appropriate starterId values
3. Add the partition pinning configuration properties  
4. Deploy and verify that starters are processing jobs only from their assigned partitions