/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.benchmarks.resilience;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class CamundaErrorClassifierTest {

  @Test
  void testIsResourceExhausted_withResourceExhaustedException() {
    // Given
    StatusRuntimeException exception = new StatusRuntimeException(
        Status.RESOURCE_EXHAUSTED.withDescription("Backpressure detected")
    );

    // When
    boolean result = CamundaErrorClassifier.isResourceExhausted(exception);

    // Then
    assertTrue(result, "Should detect RESOURCE_EXHAUSTED error");
  }

  @Test
  void testIsResourceExhausted_withWrappedResourceExhaustedException() {
    // Given
    StatusRuntimeException cause = new StatusRuntimeException(
        Status.RESOURCE_EXHAUSTED.withDescription("Backpressure detected")
    );
    CompletionException exception = new CompletionException(cause);

    // When
    boolean result = CamundaErrorClassifier.isResourceExhausted(exception);

    // Then
    assertTrue(result, "Should detect wrapped RESOURCE_EXHAUSTED error");
  }

  @Test
  void testIsResourceExhausted_withOtherStatusException() {
    // Given
    StatusRuntimeException exception = new StatusRuntimeException(
        Status.NOT_FOUND.withDescription("Process not found")
    );

    // When
    boolean result = CamundaErrorClassifier.isResourceExhausted(exception);

    // Then
    assertFalse(result, "Should not detect non-RESOURCE_EXHAUSTED status as resource exhausted");
  }

  @Test
  void testIsResourceExhausted_withNonStatusException() {
    // Given
    RuntimeException exception = new RuntimeException("Some other error");

    // When
    boolean result = CamundaErrorClassifier.isResourceExhausted(exception);

    // Then
    assertFalse(result, "Should not detect non-status exception as resource exhausted");
  }

  @Test
  void testUnwrapCompletion_withCompletionException() {
    // Given
    RuntimeException cause = new RuntimeException("Underlying cause");
    CompletionException exception = new CompletionException(cause);

    // When
    Throwable result = CamundaErrorClassifier.unwrapCompletion(exception);

    // Then
    assertEquals(cause, result, "Should unwrap CompletionException");
  }

  @Test
  void testUnwrapCompletion_withNonCompletionException() {
    // Given
    RuntimeException exception = new RuntimeException("Direct exception");

    // When
    Throwable result = CamundaErrorClassifier.unwrapCompletion(exception);

    // Then
    assertEquals(exception, result, "Should return same exception if not CompletionException");
  }

  @Test
  void testUnwrapCompletion_withCompletionExceptionNoCause() {
    // Given
    CompletionException exception = new CompletionException(null);

    // When
    Throwable result = CamundaErrorClassifier.unwrapCompletion(exception);

    // Then
    assertEquals(exception, result, "Should return same exception if no cause");
  }

  @Test
  void testIsResourceExhausted_withUnavailableException() {
    // Given
    StatusRuntimeException exception = new StatusRuntimeException(
        Status.UNAVAILABLE.withDescription("Service unavailable")
    );

    // When
    boolean result = CamundaErrorClassifier.isResourceExhausted(exception);

    // Then
    assertFalse(result, "Should not detect UNAVAILABLE as resource exhausted");
  }
}
