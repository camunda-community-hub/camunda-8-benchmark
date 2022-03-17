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
 

## Configuration Properties

See https://github.com/camunda-community-hub/camunda-cloud-benchmark/blob/main/src/main/resources/application.properties


# Assumptions

The project makes some assumptions

* Acquire and complete jobs as much as possible, as it is important that all tasks are completed and no pile of tasks is build up.
* As Job Completion does not get backpressure, a cluster utilized with jobs will not allow a big start rate
* Everything is executed asynchronously
* No "waitForResults" as this is currently pretty inefficient

# Todos

- Extract stuff so that it can be used as library and provide an example (Benchmark Starter)
- Get information about job activation back pressure
- Only pick up jobs from "my" process
- Wait for results & count completions
- Check if start exceptions are counted correctly
- Document properties and examples
  - Process Model from URL
  - Payload from URL
  - Pool Size Parameters