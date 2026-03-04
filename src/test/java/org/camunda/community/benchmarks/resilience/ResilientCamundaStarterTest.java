/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.benchmarks.resilience;

import io.camunda.client.CamundaClient;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ResilientCamundaStarter focusing on configuration and component access.
 * Integration tests with actual Camunda client would require a running cluster.
 */
@ExtendWith(MockitoExtension.class)
class ResilientCamundaStarterTest {

  @Mock
  private CamundaClient client;

  private RateLimiter rateLimiter;
  private Retry retry;
  private ResilientCamundaStarter resilientStarter;

  @BeforeEach
  void setUp() {
    // Create a permissive rate limiter for testing
    RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .limitForPeriod(100)
        .timeoutDuration(Duration.ofSeconds(1))
        .build();
    rateLimiter = RateLimiter.of("test", rateLimiterConfig);

    // Create a retry that only retries on RESOURCE_EXHAUSTED
    RetryConfig retryConfig = RetryConfig.custom()
        .maxAttempts(3)
        .waitDuration(Duration.ofMillis(10))
        .retryOnException(CamundaErrorClassifier::isResourceExhausted)
        .build();
    retry = Retry.of("test", retryConfig);

    resilientStarter = new ResilientCamundaStarter(client, rateLimiter, retry);
  }

  @Test
  void testGetClient() {
    // When
    CamundaClient result = resilientStarter.getClient();

    // Then
    assertEquals(client, result, "Should return the configured client");
  }

  @Test
  void testGetRateLimiter() {
    // When
    RateLimiter result = resilientStarter.getRateLimiter();

    // Then
    assertEquals(rateLimiter, result, "Should return the configured rate limiter");
    assertEquals("test", result.getName(), "Rate limiter should have correct name");
  }

  @Test
  void testGetRetry() {
    // When
    Retry result = resilientStarter.getRetry();

    // Then
    assertEquals(retry, result, "Should return the configured retry");
    assertEquals("test", result.getName(), "Retry should have correct name");
  }

  @Test
  void testRateLimiterConfiguration() {
    // Verify rate limiter has expected configuration
    RateLimiter.Metrics metrics = rateLimiter.getMetrics();
    assertEquals(100, rateLimiter.getRateLimiterConfig().getLimitForPeriod(), 
        "Should have correct limit for period");
  }

  @Test
  void testRetryConfiguration() {
    // Verify retry has expected configuration
    RetryConfig config = retry.getRetryConfig();
    assertEquals(3, config.getMaxAttempts(), "Should have correct max attempts");
  }

  @Test
  void testComponentsNotNull() {
    // All components should be properly initialized
    assertNotNull(resilientStarter.getClient(), "Client should not be null");
    assertNotNull(resilientStarter.getRateLimiter(), "Rate limiter should not be null");
    assertNotNull(resilientStarter.getRetry(), "Retry should not be null");
  }
}
