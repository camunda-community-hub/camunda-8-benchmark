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

import java.util.concurrent.CompletionException;

/**
 * Utility class for classifying Camunda-related errors.
 * Helps identify specific gRPC error types like RESOURCE_EXHAUSTED for backpressure handling.
 */
public final class CamundaErrorClassifier {

  private CamundaErrorClassifier() {
    // Utility class - no instantiation
  }

  /**
   * Checks if the given throwable represents a RESOURCE_EXHAUSTED error from Camunda/Zeebe.
   * This typically indicates that the cluster is under backpressure.
   *
   * @param t the throwable to check
   * @return true if the error is RESOURCE_EXHAUSTED, false otherwise
   */
  public static boolean isResourceExhausted(Throwable t) {
    t = unwrapCompletion(t);

    if (t instanceof StatusRuntimeException sre) {
      return sre.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED;
    }
    return false;
  }

  /**
   * Unwraps CompletionException to get the underlying cause.
   *
   * @param t the throwable to unwrap
   * @return the underlying cause if it's a CompletionException, otherwise the original throwable
   */
  public static Throwable unwrapCompletion(Throwable t) {
    if (t instanceof CompletionException && t.getCause() != null) {
      return t.getCause();
    }
    return t;
  }
}
