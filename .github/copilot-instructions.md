# Camunda 8 Benchmark (c8b)

Camunda 8 Benchmark is a Spring Boot application for load testing Camunda Platform 8 clusters. It starts process instances at configurable rates, completes tasks, and provides comprehensive metrics via Prometheus/Grafana.

**ALWAYS reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.**

## Working Effectively

### Bootstrap, Build, and Test the Repository

**CRITICAL BUILD COMMANDS** - Set appropriate timeouts for all operations:

```bash
# Verify environment
java -version  # Must be Java 17+
mvn -version   # Must be Maven 3.0+

# Build the project - NEVER CANCEL: Build takes 5-15 minutes depending on network
mvn clean install -B --no-transfer-progress
# Alternative if above fails due to network issues:
mvn clean compile -B --no-transfer-progress

# Run tests - NEVER CANCEL: Tests take 2-5 minutes
mvn test -B --no-transfer-progress
# Alternative parallel execution:
mvn clean verify -B --no-transfer-progress -T4
```

**TIMEOUT SETTINGS**: Always use minimum 900 seconds (15 minutes) timeout for build commands and 300 seconds (5 minutes) for test commands.

**NETWORK LIMITATIONS**: If Maven fails with "No address associated with hostname" or DNS errors, the build cannot complete due to firewall restrictions. Document this limitation rather than attempting workarounds.

### Run the Application

**ALWAYS run the build steps first before attempting to run the application.**

```bash
# Run locally (requires Zeebe cluster connection)
mvn spring-boot:run

# Alternative using exec plugin
mvn exec:java

# Run with custom configuration
mvn spring-boot:run -Dspring-boot.run.arguments="--benchmark.startPiPerSecond=10 --benchmark.taskCompletionDelay=100"
```

**Connection Requirements**: The application requires a Camunda 8 cluster (local, SaaS, or self-managed). Configure connection in `src/main/resources/application.properties`.

### Docker Operations

```bash
# Build Docker image - NEVER CANCEL: Takes 10-20 minutes
docker build . --tag camunda-8-benchmark:local

# Run via Docker
docker run camunda-8-benchmark:local

# Using Makefile
make all  # Builds Docker image
```

**TIMEOUT SETTINGS**: Set 1200+ seconds for Docker builds.

### Monitoring and Metrics

```bash
# Start local Grafana/Prometheus stack - NEVER CANCEL: Takes 2-5 minutes to pull images
cd grafana
docker compose up

# Access monitoring (after containers are running)
# Prometheus: http://localhost:9090
# Grafana: http://localhost:3000 (admin/admin)
# App metrics: http://localhost:8088/actuator/prometheus
```

**TIMEOUT SETTINGS**: Set 300+ seconds for Docker Compose operations on first run.

## Validation

**MANUAL VALIDATION REQUIREMENT**: After making any changes to benchmark logic, job workers, or process deployment:

1. **Always test basic functionality**:
   ```bash
   # Start the application and verify it doesn't crash on startup
   mvn spring-boot:run
   # Let it run for at least 30 seconds, check logs for errors
   ```

2. **Test specific scenarios based on changes**:
   - **Process deployment changes**: Verify BPMN files deploy correctly and check logs for deployment success
   - **Job worker changes**: Start application, ensure jobs are activated and completed (monitor metrics)
   - **Configuration changes**: Test with different parameter values and verify they take effect
   - **Metric changes**: Check Prometheus endpoint responds at http://localhost:8088/actuator/prometheus

3. **CRITICAL APPLICATION STARTUP VALIDATION**:
   ```bash
   # Start application and monitor startup - NEVER CANCEL: Startup takes 30-90 seconds
   mvn spring-boot:run
   # Watch for these SUCCESS indicators in logs:
   # - "Started BenchmarkApplication"
   # - Process deployment messages
   # - Worker registration messages
   # Let run for minimum 2 minutes to verify stability
   ```

4. **Always run the full test suite before completing changes**:
   ```bash
   mvn test -B --no-transfer-progress
   ```

**NEVER skip validation steps**. The application interacts with external Camunda clusters, and runtime errors may not surface until execution.

## Common Tasks

### Key Project Structure

```
src/main/java/org/camunda/community/benchmarks/
├── BenchmarkApplication.java          # Main Spring Boot application
├── JobWorker.java                     # Handles job completion with configurable delays
├── ProcessDeployer.java               # Deploys BPMN processes and injects job types
├── StartPiScheduler.java              # Schedules process instance creation
├── StartPiExecutor.java               # Executes process instance starts
├── StatisticsCollector.java           # Collects and reports metrics
└── config/
    ├── BenchmarkConfiguration.java    # Main configuration properties
    ├── AsyncConfiguration.java        # Thread pool configuration
    └── MicrometerConfiguration.java   # Metrics configuration

src/main/resources/
├── application.properties             # Main configuration file
└── bpmn/                             # BPMN process definitions
    ├── typical_process.bpmn          # Default 10-task process
    ├── typical_process_10_jobtypes.bpmn  # Process with unique job types
    └── typical_payload.json          # Default process payload

src/test/java/                        # 4 test classes, 359 lines total
├── ProcessDeployerTest.java          # Tests job type injection
├── ProcessDeployerIntegrationTest.java  # Tests BPMN processing
└── ProcessDeployerExistingFilesTest.java  # Tests existing file handling
```

### Essential Configuration Properties

**Always check `src/main/resources/application.properties` for current defaults:**

```properties
# Core benchmark settings
benchmark.startPiPerSecond=1           # Process instances per second
benchmark.taskCompletionDelay=150      # Job completion delay in milliseconds
benchmark.multipleJobTypes=10          # Number of unique job types (recommended: 8-10)
benchmark.warmupPhaseDurationMillis=300000  # 5-minute warmup phase
benchmark.startWorkers=true            # Enable job workers
benchmark.autoDeployProcess=true       # Auto-deploy BPMN resources

# Connection configuration (choose one)
camunda.client.mode=self-managed       # For local/self-managed
# camunda.client.mode=saas             # For Camunda SaaS
# camunda.client.mode=simple           # For local development

# Zeebe configuration
camunda.client.zeebe.base-url=http://localhost:26500
camunda.client.zeebe.defaults.max-jobs-active=2000
camunda.client.zeebe.execution-threads=100
```

### Testing Changes

**ALWAYS follow this testing sequence**:

1. **Individual test classes**:
   ```bash
   mvn test -Dtest=ProcessDeployerTest              # Job type injection tests
   mvn test -Dtest=ProcessDeployerIntegrationTest   # BPMN processing tests  
   mvn test -Dtest=ProcessDeployerExistingFilesTest # File handling tests
   mvn test -Dtest=StartMessageScenarioSchedulerTest # Message scenario tests
   ```

2. **Full test suite**: `mvn test -B --no-transfer-progress`
3. **Manual runtime test**: Start application and monitor for 60+ seconds

**Test Coverage**: 4 test classes with 359 total lines covering process deployment, job type injection, and message scenarios.

### Modifying BPMN Processes

**When changing process definitions**:

1. Update BPMN files in `src/main/resources/bpmn/`
2. Ensure service tasks follow job type conventions:
   - Use `benchmark-task-{TaskId}` for unique job types
   - Mark last task with `benchmark-task-completed`
3. Test job type injection: `mvn test -Dtest=ProcessDeployerTest`
4. Always validate deployed processes work with job workers

### Working with Job Types

**Key principle**: Use multiple unique job types (8-10) for better load distribution across brokers.

- `benchmark.multipleJobTypes=10` creates workers for `benchmark-task-1` through `benchmark-task-10`
- ProcessDeployer automatically injects unique job types for service tasks without existing types
- Always preserve existing job types in BPMN files

### Kubernetes Deployment

```bash
# Deploy monitoring infrastructure
kubectl apply -k k8s/ -n monitoring

# Deploy benchmark application
kubectl apply -f k8s/benchmark.yaml

# Access services via port forwarding
kubectl --namespace monitoring port-forward svc/prometheus-service 9090
kubectl --namespace monitoring port-forward svc/grafana 3000
```

## Build and CI Information

### Expected Timing

- **Clean build**: 5-15 minutes (depending on network and dependency resolution)
- **Test execution**: 2-5 minutes
- **Docker build**: 10-20 minutes  
- **Warmup phase**: 5 minutes (configured default)

**CRITICAL**: NEVER CANCEL builds or tests. Use timeouts of 15+ minutes for builds and 5+ minutes for tests.

### CI/CD Pipeline

The project uses GitHub Actions with two workflows:

- **ci.yml**: Pull request validation (`mvn clean verify -T4`)
- **ci-pipeline.yml**: Main branch builds (`mvn clean install`)

**Always run formatting and linting**:
```bash
# The project uses Maven plugins for code quality
mvn clean verify  # Includes license checks and code formatting validation
```

### Known Limitations

1. **Network connectivity**: Maven builds may fail in restricted network environments
2. **Docker registry access**: Docker builds require internet access for base images
3. **Zeebe cluster requirement**: Application needs active Camunda 8 cluster for full functionality
4. **Maven Central deployment**: Currently disabled (see issue #149)

## Troubleshooting

### Build Failures

- **DNS/Network errors**: Document as limitation, cannot be resolved locally
- **Test failures**: Check for process deployment issues or job type conflicts  
- **License check failures**: Ensure new files have Apache 2.0 headers
- **Docker base image errors**: Network connectivity issues prevent image pulls

### Runtime Issues

- **Connection errors**: Verify Zeebe cluster accessibility and configuration
- **Job activation timeouts**: Check cluster capacity and backpressure settings
- **Process deployment failures**: Validate BPMN syntax and job type consistency
- **Port conflicts**: Ensure ports 8088, 3000, 9090 are available for local services

**Always check application logs at startup and metrics endpoint (http://localhost:8088/actuator/health) before debugging further.**