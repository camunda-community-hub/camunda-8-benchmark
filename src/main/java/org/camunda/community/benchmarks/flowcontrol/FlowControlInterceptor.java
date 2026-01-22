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
 *
 * @see <a href="https://bucket4j.com/">Bucket4j documentation</a>
 */
public class FlowControlInterceptor implements ClientInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(FlowControlInterceptor.class);

    private final Bucket bucket;
    private final int backpressurePenalty;

    /**
     * Creates a new flow control interceptor.
     *
     * @param bucket              The Bucket4j bucket for rate limiting
     * @param backpressurePenalty Number of tokens to consume when backpressure is detected
     */
    public FlowControlInterceptor(Bucket bucket, int backpressurePenalty) {
        this.bucket = bucket;
        this.backpressurePenalty = backpressurePenalty;
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
                Listener<RespT> wrappedListener = new BackpressureListener<>(responseListener, method);

                super.start(wrappedListener, headers);
            }
        };
    }

    /**
     * A listener that handles backpressure responses by penalizing the token bucket.
     * <p>
     * When a RESOURCE_EXHAUSTED status is received, this listener consumes additional tokens
     * from the bucket, which slows down other threads waiting for tokens.
     * <p>
     * Note: onClose is called when the gRPC call has completed (either successfully or with error).
     * At this point, no request resources need to be cleaned up.
     */
    private class BackpressureListener<RespT>
            extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {

        private final MethodDescriptor<?, RespT> method;

        BackpressureListener(Listener<RespT> delegate, MethodDescriptor<?, RespT> method) {
            super(delegate);
            this.method = method;
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            if (status.getCode() == Status.Code.RESOURCE_EXHAUSTED) {
                // Penalize the bucket to slow down other waiting threads
                bucket.consumeIgnoringRateLimits(backpressurePenalty);

                LOG.debug("Backpressure detected for method: {}. Penalized bucket by {} tokens.",
                        method.getFullMethodName(), backpressurePenalty);
            }
            // Always pass the status to the original listener
            super.onClose(status, trailers);
        }
    }
}
