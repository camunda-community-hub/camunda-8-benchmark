# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Camunda 8 Benchmark (c8b) — a Spring Boot application that load-tests Camunda Platform 8 / Zeebe clusters. It starts process instances at a configurable rate, runs job workers that complete the resulting tasks, optionally evaluates DMN decisions at a configurable rate, adapts start rates based on measured backpressure, and exposes Prometheus/Micrometer metrics.

## Build, test, run

Requires Java 21 and Maven 3.0+.

```bash
mvn clean install              # full build + tests
mvn clean compile              # compile only (faster, e.g. when offline)
mvn test                       # run all tests
mvn test -Dtest=ProcessDeployerTest      # run a single test class
mvn test -Dtest=ProcessDeployerTest#someMethod   # run a single test method
mvn clean verify -T4           # what CI runs on PRs (parallel build, includes license header check)
mvn spring-boot:run            # run the app locally (needs a reachable Camunda 8 cluster)
```

Configuration lives in `src/main/resources/application.properties` and is read as `benchmark.*` / `camunda.client.*` properties (see `BenchmarkConfiguration`). Point it at a cluster before running — self-managed, SaaS, or a local `simple` cluster.

The `license-maven-plugin` enforces an Apache-2.0 header on source files; `mvn clean verify` will fail if it's missing on a new file.

`mvn clean verify` (and `mvn clean install`, since it passes through the `verify` phase too) also runs `*IT` integration tests via `maven-failsafe-plugin`. Several of these (`BackpressureStrategyIT`, `NoneStrategyIT`, `FlowControlIntegrationIT`) spin up a real Zeebe engine per test class via `camunda-process-test-spring`/Testcontainers — **Docker must be running locally** for those commands to succeed; each class takes roughly 20-50s to start its container. `mvn test` (surefire only) stays Docker-free.

CI (`.github/workflows/ci.yml`) runs `mvn clean verify -B --no-transfer-progress -T4` on every push/PR to non-main branches. `.github/workflows/ci-pipeline.yml` builds/publishes on `main` and on releases (Docker push to `camundacommunityhub/camunda-8-benchmark`, tag `main` or the release tag; Maven Central deploy is currently disabled, see issue #149).

Docker: images are built via the Spring Boot Maven plugin's Cloud Native Buildpacks support (`mvn spring-boot:build-image`), not a Dockerfile — there isn't one in this repo. Override the tag with `-Dspring-boot.build-image.imageName=<name>:<tag>`; see `Makefile` for the tag pushed to the internal GCR test registry. Local metrics stack: `cd grafana && docker compose up` (Prometheus on :9090, Grafana on :3000, app metrics at `http://localhost:8088/actuator/prometheus`).

## Architecture

Everything lives under `org.camunda.community.benchmarks`. `BenchmarkApplication` is the Spring Boot entry point; after the context is up it explicitly triggers `ProcessDeployer.autoDeploy()` and `JobWorker.startWorkers()` (deliberately not `@PostConstruct`, since the Camunda client/context isn't ready that early).

The app talks to the cluster via `io.camunda.client.CamundaClient` (from `camunda-spring-boot-starter`/`camunda-client-java` — the unified Camunda 8 client, not the older spring-zeebe `ZeebeClient`).

Four independent load-generating loops run concurrently, all async (no synchronous/blocking client calls, no `waitForResults`):

- **`StartPiScheduler`** / **`decision.StartDecisionScheduler`** — both extend `common.BenchmarkScheduler`, which owns a `@Scheduled` tick every 10ms deciding how many instances to start this tick (batching so it can hit rates > 100/s) and delegates to an abstract `startInstances()`. `BenchmarkScheduler` itself extends `common.RateCalculator`, which every 30s (after an optional warmup period) self-adjusts the target rate via `startRateAdjustmentStrategy`: `none` (fixed), `backpressure` (react to `StatisticsCollector`'s backpressure meter, the recommended/default-ish strategy), or `jobRatio` (compares job-completion vs PI-start rate; noted in code as not yet yielding good results). Two more values, `backoff` and `autoTune`, bypass `StartPiScheduler`/`RateCalculator` entirely in favor of a Bucket4j-based mechanism — see Flow control strategies below. `StartDecisionScheduler` is opt-in via `benchmark.startDecisions=true` and, since there's no Bucket4j equivalent for it yet, is disabled under `backoff`/`autoTune` (it would otherwise hit `RateCalculator.adjustStartRates()`'s `else throw` branch every 30s).
- **`StartPiExecutor`** / **`decision.StartDecisionExecutor`** — both extend `common.BenchmarkExecutor` (shared `startInstance()` contract + a `tryReadVariables` helper for loading the payload JSON). `StartPiExecutor` starts a process instance, either directly (`newCreateInstanceCommand`) or, when partition pinning is enabled, by publishing a message with a correlation key engineered to land on a specific partition (see Partition pinning below). `StartDecisionExecutor` instead evaluates a DMN decision (`newEvaluateDecisionCommand`, `benchmark.dmnDecisionId`) — a separate, simpler load-generation path for DMN-only benchmarking. Both wrap every command in `RefactoredCommandWrapper` for retry/backoff/metrics.
- **`JobWorker`** — registers job workers and completes jobs after a configurable delay (`benchmark.taskCompletionDelay`, or a per-instance `delay` variable). The *last* job type in a chain is marked to also record process-instance completion (via the `-completed` suffix convention), which is how end-to-end cycle time is measured.
- **`StartMessageScenarioScheduler`** — optional, only active when `benchmark.messageScenario` is set; replays a scripted sequence of correlated messages (`Message`/`MessagesScenario` models) instead of/alongside the PI-starting loop.

**`ProcessDeployer`** deploys the configured BPMN resource(s) on startup (`benchmark.autoDeployProcess`). Before deploying it rewrites the BPMN: `injectUniqueJobTypes` uses the Zeebe BPMN model API to add a `zeebe:taskDefinition` to any service task that doesn't already have one (so you can point at an arbitrary process model without hand-editing job types), then applies any configured `jobTypesToReplace`/`bpmnProcessIdToReplace` string substitutions. `JobWorker.startWorkers()` mirrors this by parsing the same BPMN via `BpmnJobTypeParser` to discover static job types automatically, in addition to whatever's configured via `benchmark.jobType`/`benchmark.multipleJobTypes` — BPMN-only job types get a plain worker, while the single configured job type (when there's exactly one) also gets `-completed` and per-starter-ID variants registered.

**`StatisticsCollector`** is the shared metrics hub: Dropwizard Metrics (`MetricRegistry`) is used internally for rate/meter calculations (one-minute rates etc. feed the backpressure-adjustment logic), while Micrometer/Prometheus mirrors the same counters/timers for external scraping via Spring Actuator. It tracks both PI-related counters (`pi_*`) and, for the DMN load-testing path, DI/decision-evaluation counters (`di_*`).

**`strategy.BenchmarkStartPiExceptionHandlingStrategy`** / **`strategy.BenchmarkCompleteJobExceptionHandlingStrategy`** / **`strategy.BenchmarkStartDecisionExceptionHandlingStrategy`** extend the client's `DefaultCommandExceptionHandlingStrategy` to record exception metrics and treat certain gRPC statuses as backpressure (a non-retried signal) rather than an error: `RESOURCE_EXHAUSTED` for PI-starting/job-completion, plus `UNAVAILABLE` for decision evaluation (noted in code as frequent for DMN since the decision is reparsed on every evaluation, which raises latency under load). The PI/job strategies also de-duplicate log spam (full stacktrace only on first occurrence of an exception type, message-only afterward).

**`refactoring.RefactoredCommandWrapper`** is a local copy of the client SDK's `CommandWrapper` (see comment in the file — the intent is to eventually upstream a fix rather than maintain a fork).

**`config.BenchmarkConfiguration`** binds all `benchmark.*` properties; its getters/setters are Lombok-generated (`@Getter`/`@Setter` on the class) rather than hand-written — see the class-level comment there if an IDE reports them as missing (it means the Lombok plugin/annotation processing isn't set up, not a real code issue).

### Partition pinning

An opt-in mode (`benchmark.enablePartitionPinning=true`, see `PARTITION_PINNING.md`) where each benchmark starter instance is assigned a fixed subset of Zeebe partitions and only generates load for those, to reduce cross-broker traffic when running many starter replicas. `PartitionHashUtil` implements Zeebe's own partition-hashing (djb2 via `SubscriptionUtil`) to compute correlation keys that land on a chosen partition, and to derive a starter's target partitions from `benchmark.starterId` (must end in `-<N>`) + `benchmark.partitionCount` + `benchmark.numberOfStarters`. When enabled, process instances are started by publishing a message (`StartBenchmarkProcess`) instead of `newCreateInstanceCommand`, so the target BPMN needs a message start event (see `seq-2tasks-msg-start-no-job-types.bpmn`), and job types get prefixed with the starter ID so each starter's workers only pick up their own partitions' jobs.

### Flow control strategies (`backoff` / `autoTune`)

Opt-in via `benchmark.startRateAdjustmentStrategy=backoff` or `=autoTune` (see README "Bucket4j Flow Control Strategies"); PI-starting only, not available for `StartDecisionScheduler`/DMN load testing. Selecting either activates `config.FlowControlConfiguration` (and deactivates `StartPiScheduler`) via matching `@ConditionalOnExpression`s from the shared `config.FlowControlStrategyExpressions` constants — keep those two expressions in sync if a new strategy is added. The wiring, all under `flowcontrol`:

- **`RateGoal`** — a small shared mutable holder for the current target rate (PI/s), used by both pieces below instead of them depending on each other (avoids a circular dependency).
- **`FlowControlInterceptor`** — a gRPC `ClientInterceptor` auto-registered on the whole `CamundaClient` (via `camunda-spring-boot-starter`'s `List<ClientInterceptor>` injection in `CamundaClientProdAutoConfiguration` — so it's global, not scoped to PI-start calls only). On `RESOURCE_EXHAUSTED` it penalizes the shared Bucket4j `Bucket` by tokens proportional to the *live* `RateGoal` (recomputed each time, not fixed at startup, so the penalty stays right-sized as `autoTune` changes the rate). It does **not** forward to `StatisticsCollector`'s `pi_backpressure` meter itself — `strategy.BenchmarkStartPiExceptionHandlingStrategy` already does that, unconditionally, for actual PI-start commands specifically; forwarding from both would double-count.
- **`Bucket4jPiScheduler`** — runs a single persistent virtual thread that blocks on the bucket (`bucket.asBlocking().consume(1)`) and calls `StartPiExecutor.startInstance()` per token. One thread is enough because `startInstance()` is fire-and-forget/async — pacing comes entirely from the bucket, not from thread count. Under `autoTune` only, `adjustRate()` (a `@Scheduled` tick every 10s) runs a TCP-style slow-start-then-AIMD search: it reads `StatisticsCollector`'s rolling `pi_started`/`pi_backpressure` one-minute rates (the same meters `common.RateCalculator`'s classic `backpressure` strategy uses — chosen over the interceptor's own raw counters because those decay smoothly instead of being hard-reset every tick, avoiding a feedback-lag blind spot) and grows the rate exponentially until the *first* tick where backpressure exceeds `benchmark.maxBackpressurePercentage`; from then on it assumes the cluster's ceiling is roughly fixed for the run, cuts hard once, and permanently switches to small additive steps for the rest of the run. This replaced an earlier always-exponential version that had no such bound and could run away by orders of magnitude within minutes (observed causing broker-side backpressure and OOM in production) before a slow-to-resolve rejection ever caught up in the accounting.

### Job type / BPMN conventions

- Service tasks without an explicit `zeebe:taskDefinition` get one auto-injected by `ProcessDeployer` at deploy time — you generally don't need to hand-author job types in a custom process model.
- The last task in a chain should use `<jobType>-completed` so the benchmark can measure full cycle time (see README "Defining your own process").
- `benchmark.multipleJobTypes=N` spreads load across `N` distinct job types (`benchmark-task-1` … `benchmark-task-N`), which the README notes distributes work more evenly across brokers than a single job type.
- `benchmark.starterId` ties a starter instance to process/job-type variants (`<jobType>-<starterId>`), used both for "sticky" multi-starter setups and for partition pinning.

## Tests

Tests live in `src/test/java/org/camunda/community/benchmarks/...`, mirroring the main package layout (JUnit 5 + Mockito). Notable ones: `ProcessDeployerTest`/`ProcessDeployerIntegrationTest`/`ProcessDeployerExistingFilesTest`/`ProcessDeployerPartitionPinningTest` (BPMN rewriting/job-type injection), `PartitionHashUtilTest` (partition math), `BpmnJobTypeParserTest`, `StartMessageScenarioSchedulerTest`, `JobWorkerTest`/`JobWorkerIntegrationTest`, `PartitionPinningE2ETest`, `BenchmarkConfigurationTest`.

Flow control (see above): `FlowControlInterceptorTest`/`Bucket4jPiSchedulerTest`/`FlowControlConditionalBeanTest` are plain unit tests (Mockito/`ApplicationContextRunner`, no Docker). `BackpressureStrategyIT`/`NoneStrategyIT`/`FlowControlIntegrationIT` are `*IT` classes run by `maven-failsafe-plugin` — each boots a real Zeebe engine via `camunda-process-test-spring`/Testcontainers to verify the right beans/schedulers are active per `startRateAdjustmentStrategy` value (see the Docker note under Build, test, run).
