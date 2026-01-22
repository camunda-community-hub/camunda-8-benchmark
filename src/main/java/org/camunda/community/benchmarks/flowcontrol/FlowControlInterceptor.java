/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Apache License, Version 2.0; you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.community.benchmarks.flowcontrol;

import io.github.bucket4j.Bucket;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientCall.Listener;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A gRPC client interceptor that implements flow control using Bucket4j.
 * <p>
 * This interceptor provides two key features:
 * <ol>
 *   <li><strong>Rate Limiting:</strong> Before each call, it waits for a token from the bucket.
 *       This effectively limits the rate of outgoing requests. With Java 21 virtual threads,
 *       this waiting is efficient and doesn't block OS threads.</li>
 *   <li><strong>Backpressure Handling:</strong> When a RESOURCE_EXHAUSTED status is received from
 *       Zeebe, the interceptor penalizes the bucket by consuming additional tokens. This creates
 *       a feedback loop that automatically slows down other waiting requests.</li>
 * </ol>
 * <p>
 * Optionally, the interceptor can also retry failed calls with exponential backoff when
 * RESOURCE_EXHAUSTED errors are received.
 *
 * @see <a href="https://bucket4j.com/">Bucket4j documentation</a>
 */
public class FlowControlInterceptor implements ClientInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(FlowControlInterceptor.class);

    private final Bucket bucket;
    private final int backpressurePenalty;
    private final boolean retryEnabled;
    private final int maxRetries;
    private final long initialBackoffMs;

    /**
     * Creates a new flow control interceptor.
     *
     * @param bucket              The Bucket4j bucket for rate limiting
     * @param backpressurePenalty Number of tokens to consume when backpressure is detected
     * @param retryEnabled        Whether to retry on RESOURCE_EXHAUSTED errors
     * @param maxRetries          Maximum number of retries (if retry is enabled)
     * @param initialBackoffMs    Initial backoff delay in milliseconds (if retry is enabled)
     */
    public FlowControlInterceptor(Bucket bucket, int backpressurePenalty, boolean retryEnabled,
                                  int maxRetries, long initialBackoffMs) {
        this.bucket = bucket;
        this.backpressurePenalty = backpressurePenalty;
        this.retryEnabled = retryEnabled;
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // Wait for a token before starting the call
                // With Virtual Threads (Java 21), this parks the virtual thread efficiently
                bucket.asBlocking().consumeUninterruptibly(1);

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Token acquired for method: {}", method.getFullMethodName());
                }

                // Wrap the listener to detect backpressure responses
                Listener<RespT> wrappedListener = new BackpressureListener<>(
                        responseListener, method, callOptions, next, headers, 0);

                super.start(wrappedListener, headers);
            }
        };
    }

    /**
     * A listener that handles backpressure responses and optionally retries failed calls.
     */
    private class BackpressureListener<RespT>
            extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {

        private final MethodDescriptor<?, RespT> method;
        private final CallOptions callOptions;
        private final Channel channel;
        private final Metadata headers;
        private final int retryCount;

        BackpressureListener(Listener<RespT> delegate, MethodDescriptor<?, RespT> method,
                             CallOptions callOptions, Channel channel, Metadata headers, int retryCount) {
            super(delegate);
            this.method = method;
            this.callOptions = callOptions;
            this.channel = channel;
            this.headers = headers;
            this.retryCount = retryCount;
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            if (status.getCode() == Status.Code.RESOURCE_EXHAUSTED) {
                handleBackpressure(status, trailers);
            } else {
                super.onClose(status, trailers);
            }
        }

        private void handleBackpressure(Status status, Metadata trailers) {
            // Penalize the bucket to slow down other waiting threads
            bucket.consumeIgnoringRateLimits(backpressurePenalty);

            LOG.debug("Backpressure detected for method: {}. Penalized bucket by {} tokens.",
                    method.getFullMethodName(), backpressurePenalty);

            if (retryEnabled && retryCount < maxRetries) {
                // Calculate exponential backoff delay
                long sleepTime = initialBackoffMs * (long) Math.pow(2, retryCount);

                LOG.debug("Retrying method: {} after {}ms (attempt {}/{})",
                        method.getFullMethodName(), sleepTime, retryCount + 1, maxRetries);

                try {
                    // Sleep with exponential backoff - virtual threads park efficiently here
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    super.onClose(status, trailers);
                    return;
                }

                // Wait for a new token before retrying
                bucket.asBlocking().consumeUninterruptibly(1);

                // Create a new call for the retry
                ClientCall<?, RespT> retryCall = channel.newCall(method, callOptions);
                BackpressureListener<RespT> retryListener = new BackpressureListener<>(
                        delegate(), method, callOptions, channel, headers, retryCount + 1);
                retryCall.start(retryListener, headers);
            } else {
                // No more retries, pass the error to the application
                if (retryEnabled && retryCount >= maxRetries) {
                    LOG.warn("Max retries ({}) exhausted for method: {}", maxRetries, method.getFullMethodName());
                }
                super.onClose(status, trailers);
            }
        }
    }
}
