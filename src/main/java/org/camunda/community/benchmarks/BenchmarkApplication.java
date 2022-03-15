package org.camunda.community.benchmarks;

import io.camunda.zeebe.spring.client.EnableZeebeClient;
import io.camunda.zeebe.spring.client.annotation.ZeebeDeployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableZeebeClient
@EnableScheduling
@ZeebeDeployment(resources = "classpath:bpmn/typical_process.bpmn")
class BenchmarkApplication  {

    public static void main(String[] args) {
        SpringApplication.run(BenchmarkApplication.class, args);
    }

}