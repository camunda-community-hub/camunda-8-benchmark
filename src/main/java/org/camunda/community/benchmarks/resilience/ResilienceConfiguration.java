/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.benchmarks.resilience;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.function.Predicate;

import static org.camunda.community.benchmarks.resilience.CamundaErrorClassifier.isResourceExhausted;

/**
 * Configuration for Resilience4j patterns to handle backpressure and rate limiting
 * when starting process instances in Camunda.
 */
@Configuration
@ConditionalOnProperty(name = "benchmark.resilience.enabled", havingValue = "true", matchIfMissing = false)
public class ResilienceConfiguration {

  @Value("${benchmark.resilience.ratelimiter.limit-for-period:50}")
  private int limitForPeriod;

  @Value("${benchmark.resilience.ratelimiter.limit-refresh-period:1}")
  private long limitRefreshPeriodSeconds;

  @Value("${benchmark.resilience.ratelimiter.timeout-duration:5}")
  private long timeoutDurationSeconds;

  @Value("${benchmark.resilience.retry.max-attempts:5}")
  private int maxAttempts;

  @Value("${benchmark.resilience.retry.wait-duration:200}")
  private long waitDurationMillis;

  /**
   * Creates a RateLimiter to control the rate of process instance creation.
   * Limits how many commands can be executed per time window.
   *
   * @return configured RateLimiter bean
   */
  @Bean
  public RateLimiter processStartRateLimiter() {
    RateLimiterConfig config = RateLimiterConfig.custom()
        // max X instance-starts per second
        .limitRefreshPeriod(Duration.ofSeconds(limitRefreshPeriodSeconds))
        .limitForPeriod(limitForPeriod)
        // how long callers wait for a permission before failing
        .timeoutDuration(Duration.ofSeconds(timeoutDurationSeconds))
        .build();

    return RateLimiter.of("process-start", config);
  }

  /**
   * Creates a Retry that only retries on RESOURCE_EXHAUSTED errors with backoff.
   * This handles backpressure scenarios where the Camunda cluster is temporarily overloaded.
   *
   * @return configured Retry bean
   */
  @Bean
  public Retry processStartRetry() {
    Predicate<Throwable> retryOnResourceExhausted = CamundaErrorClassifier::isResourceExhausted;

    RetryConfig config = RetryConfig.custom()
        .maxAttempts(maxAttempts) // total tries = initial + (maxAttempts - 1) retries
        .waitDuration(Duration.ofMillis(waitDurationMillis)) // base delay
        .retryExceptions(Throwable.class)     // we'll filter via predicate
        .retryOnException(retryOnResourceExhausted)
        .build();

    return Retry.of("process-start-retry", config);
  }
}
