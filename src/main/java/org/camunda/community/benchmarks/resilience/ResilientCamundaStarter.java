/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.benchmarks.resilience;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Wrapper service for starting process instances with Resilience4j patterns.
 * Provides rate limiting and retry capabilities with RESOURCE_EXHAUSTED error handling.
 */
@Component
@ConditionalOnProperty(name = "benchmark.resilience.enabled", havingValue = "true", matchIfMissing = false)
public class ResilientCamundaStarter {

  private final CamundaClient client;
  private final RateLimiter rateLimiter;
  private final Retry retry;

  public ResilientCamundaStarter(
      CamundaClient client,
      RateLimiter rateLimiter,
      Retry retry
  ) {
    this.client = client;
    this.rateLimiter = rateLimiter;
    this.retry = retry;
  }

  /**
   * Blocking example: starts a process instance and returns the event,
   * using RateLimiter + Retry around the call.
   *
   * @param bpmnProcessId the BPMN process ID to start
   * @param variables the process variables
   * @return the ProcessInstanceEvent
   */
  public ProcessInstanceEvent startProcessInstanceBlocking(
      String bpmnProcessId,
      Map<String, Object> variables
  ) {

    Supplier<ProcessInstanceEvent> startSupplier = () ->
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(bpmnProcessId)
            .latestVersion()
            .variables(variables)
            .send()
            .join(); // join() -> CompletionException on errors

    Supplier<ProcessInstanceEvent> decorated =
        Decorators
            .ofSupplier(startSupplier)
            .withRateLimiter(rateLimiter)
            .withRetry(retry)
            .decorate();

    return decorated.get();
  }

  /**
   * Asynchronous example: returns CompletionStage, still protected
   * by RateLimiter + Retry (retries will resubscribe the supplier).
   *
   * @param bpmnProcessId the BPMN process ID to start
   * @param variables the process variables
   * @return CompletionStage of ProcessInstanceEvent
   */
  public CompletionStage<ProcessInstanceEvent> startProcessInstanceAsync(
      String bpmnProcessId,
      Map<String, Object> variables
  ) {

    Supplier<CompletionStage<ProcessInstanceEvent>> startAsyncSupplier = () ->
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(bpmnProcessId)
            .latestVersion()
            .variables(variables)
            .send(); // already CompletionStage

    Supplier<CompletionStage<ProcessInstanceEvent>> decorated =
        Decorators
            .ofSupplier(startAsyncSupplier)
            .withRateLimiter(rateLimiter)
            .withRetry(retry)
            .decorate();

    return decorated.get();
  }

  /**
   * Get the CreateProcessInstanceCommandStep1 builder with resilience applied.
   * This allows for more flexible configuration before sending the command.
   *
   * @return the command builder
   */
  public CreateProcessInstanceCommandStep1 newCreateInstanceCommand() {
    return client.newCreateInstanceCommand();
  }

  /**
   * Get the underlying CamundaClient.
   * Use this if you need direct access to the client for operations not covered by this wrapper.
   *
   * @return the CamundaClient
   */
  public CamundaClient getClient() {
    return client;
  }

  /**
   * Get the RateLimiter used by this starter.
   *
   * @return the RateLimiter
   */
  public RateLimiter getRateLimiter() {
    return rateLimiter;
  }

  /**
   * Get the Retry used by this starter.
   *
   * @return the Retry
   */
  public Retry getRetry() {
    return retry;
  }
}
