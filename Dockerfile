# syntax=docker/dockerfile:1

# Why this is a hand-written Dockerfile and not `mvn spring-boot:build-image`
# (Cloud Native Buildpacks):
#
# Buildpacks' bundled JVM memory calculator hardcodes -XX:MaxDirectMemorySize to 10MB
# unless told otherwise, and there is no supported way to make it scale with the
# container's memory limit instead - confirmed with the Paketo maintainers themselves:
#   https://github.com/orgs/paketo-buildpacks/discussions/241
#   https://github.com/orgs/paketo-buildpacks/discussions/307
# The only "opt-out" is to bypass the buildpack's own launcher/exec.d mechanism
# entirely, which gives up the point of using buildpacks for the runtime side. This app
# is a gRPC/Netty-heavy load generator by design - four concurrent, non-blocking async
# load loops (StartPiScheduler/StartPiExecutor, JobWorker, StartDecisionScheduler,
# StartMessageScenarioScheduler, see CLAUDE.md) that lean on off-heap direct buffers for
# in-flight requests and job-worker streaming - so a fixed 10MB ceiling OOMs quickly
# under any real load. Don't switch this back to spring-boot:build-image without
# actually solving that upstream limitation first.
FROM maven:3.9.16-eclipse-temurin-21-alpine AS builder
WORKDIR /usr/src/app
COPY pom.xml pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:resolve-plugins dependency:resolve package -Dspring-boot.repackage.skip=true -Dmaven.test.skip=true -DskipTests -DskipChecks
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -Dspring-boot.repackage.layers.enabled=true -Dmaven.test.skip=true -DskipTests -DskipChecks
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination . --force

FROM azul/zulu-openjdk-alpine:25.0.3-25.34-jre-headless

# Run as non-root, matching what Buildpacks-produced images do out of the box.
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app
COPY --from=builder --chown=spring:spring /usr/src/app/dependencies/ ./
COPY --from=builder --chown=spring:spring /usr/src/app/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /usr/src/app/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /usr/src/app/application/ ./
USER spring:spring

# Generous default heap instead of the JVM's container-aware default of just 25% of
# available memory (-XX:MaxRAMPercentage's own default) - leaves ~25% of the container
# limit as headroom for metaspace/thread-stacks/native overhead outside the JVM's own
# accounting. Deliberately NOT setting -XX:MaxDirectMemorySize: left unset, it defaults
# to whatever -Xmx resolves to (Runtime.maxMemory()), so direct memory automatically
# scales right along with heap/container memory - this is the property that made the
# plain-JRE image feel "unconstrained" before, and it's restored here intentionally
# rather than by accident.
#
# JDK_JAVA_OPTIONS (not JAVA_TOOL_OPTIONS) carries this default deliberately: it's a
# separate env var read natively by the JVM, so deployments that set JAVA_TOOL_OPTIONS
# at runtime for benchmark.*/camunda.client.* -D overrides won't silently clobber it -
# Kubernetes/Docker container env replaces same-named vars outright, it doesn't merge.
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0"

# Exec form (no shell) so java runs as PID 1 and receives SIGTERM directly for a clean,
# graceful shutdown on pod termination/rolling deploys. This only works because runtime
# -D overrides go through JAVA_TOOL_OPTIONS (read natively by the JVM, no shell
# expansion needed) rather than a shell-expanded $JAVA_OPTIONS-style variable.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
