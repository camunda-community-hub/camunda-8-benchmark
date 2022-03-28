# Camunda Cloud Benchmark Starter

Spring Boot project to run benchmarks on Camunda Cloud.

The project 

* Starts process instances at a given rate
* The rate will be adjusted based on backpressure, so it should find a sustainable starter rate automatically
* Completes tasks in the processes


# How-to run

```bash
mvn spring-boot:run
```

Or 
```bash
docker run berndruecker/camunda-cloud-benchmark:main
```

You can configure 

- everything [Spring Zeebe](https://github.com/camunda-community-hub/spring-zeebe) understands
- additional parameters for the benchmark as listed below

The design is, that you only run this one application for driving your benchmark. 

If you benchmark big clusters and cannot produce enough load, you can simply scale this application (you might want to adjust the `benchmark.startPiReduceFactor` of the properties as backpressure is then "distributed" over various load generators)
 
## Define your process

### Measure cycle time

You can mark your last service task in the process, so that the benchmark starter will use this to measure the cycle time. While this is not a 100% correct, it is a good approximation and sufficient for typical load tests.

Therefore, just add ``-completed`` to the task type of the last service task:

```xml
 <bpmn:serviceTask id="lastTask">
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="benchmark-task-completed" />
  </bpmn:extensionElements>
```



### Sticky processes 

You can tie a process to one cluster instance of the starter (in case you need to scale those). Therefore, you need to makre sure the following configuration property is set differently for every starter instance (e.g. by using environment variables to overwrite it):

```properties
benchmark.starterId=benchmarkStarter1
```

Now you can use that startedId in your process models to tie service tasks to this instance of your starter by using the `benchmark_starter_id` process variable:

```xml
 <bpmn:serviceTask id="task1" name="task1">
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="= &#34;benchmark-task-&#34; + benchmark_starter_id" />
  </bpmn:extensionElements>
```

Normally you simply configure the task type via the modeler:

```
= "benchmark-task-" + benchmark_starter_id
```



## Configuration Properties

See https://github.com/camunda-community-hub/camunda-cloud-benchmark/blob/main/src/main/resources/application.properties

## Thoughts on load generation

### Job Acquisition, Job Completion, and Process Start

The benchmark project starts all three types of work: acquire jobs, complete those jobs and start new process instances. The different work items compete for resources on the broker.

The current configuration gives maximum priority to jobs, as it is important that all open tasks can be completed. Otherwise open tasks will pile up and you eventually might never catch up.

Zeebe itself whitelists job completions on backpressure, which means, no backpressure ever on job completions. That also means, whenever the system is busy with tasks/jobs, no process instances can be started, as this will experience a lot of backpressure.

### Synchronous And Asynchronous Work


Everything in this benchmark project is executed asynchronously using various schedulers and thread pools.

Overall, the code itself does not do very compute intensive things, but basically waits for network I/O most of the time. By using asynchronous/reactive programming, one application should be able to produce a lot of load. 

As a consequence, no synchronous code is used, and also ``waitForResults`` from Zeebe is not used at all.

### Do we need backpressure and what's an acceptable limit?

When you try to optimize for cluster throughput you want to get a number what a specific cluster can do (focusing on Zeebe for the moment). To get the maximum throughput you need some backpressure, otherwise you are not working at the limit. This is the "how far can we push this perspective".
The current assumption is 10% of backpressure gets us there.

A normal user would probably strive for 0% backpressure. If we accept only 0.5 % backpressure, we  still know that if we increase the start rate just a little bit, backpressure will kick in.

The "target" backpressure can be configured:

```properties
benchmark.maxBackpressurePercentage=10.0
```

# Collect and inspect metrics

The application provides some metrics via Spring Actuator that can be used via http://localhost:8088/actuator/prometheus for Prometheus.

The contained docker-compose starts up a prometheus/grafana combo  

```
cd grafana
docker compose up
```

This will scrape those metrics and allow inspecting it

* via Prometheus, e.g. http://localhost:9090/graph?g0.expr=startedPi_total&g0.tab=0&g0.stacked=0&g0.show_exemplars=0&g0.range_input=1h 
* via Grafana, e.g. http://localhost:3000/d/VEPGQXPnk/benchmark?orgId=1&from=now-15m&to=now

![Grafana Screenshot](grafana.png)

# Run Starter via Kubernetes and connect to Camunda SaaS

You need a Kubernetes cluster, e.g. on GCP:

````bash
gcloud init
gcloud container clusters get-credentials bernd-benchmark --zone europe-west1-b

````

In order to see something, install Prometheus and Grafana

````bash
kubectl apply -k k8s/ -n monitoring
````

You can use port forwarding to access those tools:

```bash
kubectl --namespace monitoring port-forward svc/prometheus-service 9090
kubectl --namespace monitoring port-forward svc/grafana 3000
```

Goto http://localhost:3000/

Now you can run this benchmark starter via Kubernetes, e.g. in the cluster that also runs your self-managed Camunda Cloud:

````bash
kubectl apply -f k8s/benchmark.yaml
````


# Todos

- Extract stuff so that it can be used as library and provide an example (Benchmark Starter), own code for startzing and job completion (but recognize/handle backpressure)
- Document properties and examples
  - Process Model from URL
  - Payload from URL
  - Pool Size Parameters
- Get information about job activation back pressure / Check if we need to look at JobActivation-Backoff?
```log
09:19:23.695 [grpc-default-executor-168] WARN  io.camunda.zeebe.client.job.poller - Failed to activated jobs for worker default and job type benchmark-task-benchmarkStarter1
  io.grpc.StatusRuntimeException: DEADLINE_EXCEEDED: deadline exceeded after 19.999980100s. [closed=[UNAVAILABLE], committed=[remote_addr=3e93d2f8-adf2-4d45-86f6-19581c016972.bru-3.zeebe.ultrawombat.com/34.76.29.41:443]]
  at io.grpc.Status.asRuntimeException(Status.java:535)
  at io.grpc.stub.ClientCalls$StreamObserverToCallListenerAdapter.onClose(ClientCalls.java:479)
  at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:562)
  at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:70)
  at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:743)
  at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:722)
  at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
  at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
  at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
  at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
  at java.base/java.lang.Thread.run(Thread.java:833)
```