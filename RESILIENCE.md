# Resilience4j Flow Control

This document describes the Resilience4j-based flow control feature in the Camunda 8 Benchmark application.

## Overview

The Resilience4j integration provides an alternative to the traditional backpressure strategy by using:
- **RateLimiter**: Controls the rate of process instance creation
- **Retry**: Automatically retries on `RESOURCE_EXHAUSTED` errors with configurable backoff

This makes the benchmark code more maintainable, enables new features, and serves as an example for users wanting to build flow control in their applications.

## Components

### CamundaErrorClassifier
Utility class for classifying Camunda-related gRPC errors. It identifies `RESOURCE_EXHAUSTED` errors which indicate cluster backpressure.

**Location**: `org.camunda.community.benchmarks.resilience.CamundaErrorClassifier`

**Key Methods**:
- `isResourceExhausted(Throwable)`: Checks if an error is a `RESOURCE_EXHAUSTED` gRPC status
- `unwrapCompletion(Throwable)`: Unwraps `CompletionException` to get the underlying cause

### ResilienceConfiguration
Spring configuration that creates and configures the Resilience4j beans.

**Location**: `org.camunda.community.benchmarks.resilience.ResilienceConfiguration`

**Beans**:
- `processStartRateLimiter()`: RateLimiter for controlling process start rate
- `processStartRetry()`: Retry that only retries on `RESOURCE_EXHAUSTED` errors

**Conditional**: Only active when `benchmark.resilience.enabled=true`

### ResilientCamundaStarter
Wrapper service that combines `CamundaClient` with Resilience4j patterns.

**Location**: `org.camunda.community.benchmarks.resilience.ResilientCamundaStarter`

**Methods**:
- `startProcessInstanceBlocking()`: Synchronous process instance start with resilience
- `startProcessInstanceAsync()`: Asynchronous process instance start with resilience
- `getClient()`: Access to the underlying `CamundaClient`
- `getRateLimiter()`: Access to the configured rate limiter
- `getRetry()`: Access to the configured retry

**Conditional**: Only active when `benchmark.resilience.enabled=true`

## Configuration

Add these properties to `application.properties`:

```properties
# Enable Resilience4j-based flow control
benchmark.resilience.enabled=true

# Rate limiter configuration
benchmark.resilience.ratelimiter.limit-for-period=50
benchmark.resilience.ratelimiter.limit-refresh-period=1
benchmark.resilience.ratelimiter.timeout-duration=5

# Retry configuration
benchmark.resilience.retry.max-attempts=5
benchmark.resilience.retry.wait-duration=200
```

### Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `benchmark.resilience.enabled` | `false` | Enable Resilience4j flow control |
| `benchmark.resilience.ratelimiter.limit-for-period` | `50` | Max process instances per refresh period |
| `benchmark.resilience.ratelimiter.limit-refresh-period` | `1` | Refresh period in seconds |
| `benchmark.resilience.ratelimiter.timeout-duration` | `5` | How long to wait for rate limiter permission (seconds) |
| `benchmark.resilience.retry.max-attempts` | `5` | Total attempts (initial + retries) |
| `benchmark.resilience.retry.wait-duration` | `200` | Wait duration between retries (milliseconds) |

## Usage

### Integration with StartPiExecutor

The `StartPiExecutor` automatically uses `ResilientCamundaStarter` when resilience is enabled:

```java
@Autowired(required = false)
private ResilientCamundaStarter resilientStarter;

private void startProcessInstanceDirectly(HashMap<Object, Object> variables) {
    if (config.isResilienceEnabled() && resilientStarter != null) {
        startProcessInstanceWithResilience(variables);
    } else {
        startProcessInstanceWithCommandWrapper(variables);
    }
}
```

### Behavior

#### Normal Load
- RateLimiter allows up to `limitForPeriod` instance starts per time window
- Calls proceed with minimal overhead

#### Cluster Under Backpressure (RESOURCE_EXHAUSTED)
- Retry automatically retries the start operation with configurable delays
- RateLimiter limits global throughput to avoid overwhelming the gateway
- After `maxAttempts` failures, the exception is propagated for logging/alerting

#### Other Errors (NOT_FOUND, UNAVAILABLE, etc.)
- Not matched by the retry predicate → no retry, fail fast
- This ensures only backpressure scenarios trigger retries

## Example Usage

### Basic Setup

```java
@Configuration
public class MyConfiguration {
    
    @Bean
    public ResilientCamundaStarter resilientStarter(
        CamundaClient client,
        RateLimiter rateLimiter,
        Retry retry
    ) {
        return new ResilientCamundaStarter(client, rateLimiter, retry);
    }
}
```

### Using the Resilient Starter

```java
@Autowired
private ResilientCamundaStarter starter;

public void startProcess() {
    Map<String, Object> variables = Map.of("key", "value");
    
    // Synchronous
    ProcessInstanceEvent event = starter.startProcessInstanceBlocking(
        "my-process", 
        variables
    );
    
    // Asynchronous
    starter.startProcessInstanceAsync("my-process", variables)
        .thenAccept(e -> log.info("Started: {}", e.getProcessInstanceKey()))
        .exceptionally(t -> {
            log.error("Failed to start", t);
            return null;
        });
}
```

## Testing

The implementation includes comprehensive tests:

### CamundaErrorClassifierTest (8 tests)
- Tests for detecting `RESOURCE_EXHAUSTED` errors
- Tests for unwrapping `CompletionException`
- Tests for handling various gRPC status codes

### ResilientCamundaStarterTest (6 tests)
- Tests for component configuration
- Tests for bean access methods
- Tests for rate limiter and retry configuration

Run tests with:
```bash
mvn test -Dtest=CamundaErrorClassifierTest,ResilientCamundaStarterTest
```

## Benefits

1. **Maintainability**: Cleaner separation of concerns using Resilience4j patterns
2. **Configurability**: Easy to tune rate limiting and retry behavior via properties
3. **Observability**: Resilience4j provides built-in metrics and events
4. **Example Code**: Demonstrates best practices for flow control with Camunda
5. **Backward Compatible**: Disabled by default, existing behavior unchanged

## Migration

To migrate from the existing backpressure strategy:

1. Enable resilience: `benchmark.resilience.enabled=true`
2. Configure rate limiter to match your `startPiPerSecond` target
3. Tune retry settings based on your cluster's backpressure characteristics
4. Monitor behavior and adjust configuration as needed

The traditional backpressure strategy remains available when resilience is disabled.

## References

- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Resilience4j Spring Boot Integration](https://resilience4j.readme.io/docs/getting-started-3)
- [Camunda 8 Best Practices: Dealing with Backpressure](https://docs.camunda.io/docs/components/best-practices/development/dealing-with-problems-and-exceptions/#dealing-with-backpressure-from-zeebe)
