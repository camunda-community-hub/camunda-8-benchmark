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

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowControlInterceptorTest {

    @Mock
    private Channel channel;

    @Mock
    private ClientCall<Object, Object> clientCall;

    @Mock
    private MethodDescriptor<Object, Object> methodDescriptor;

    private Bucket bucket;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(channel.newCall(any(), any())).thenReturn(clientCall);
        when(methodDescriptor.getFullMethodName()).thenReturn("test/method");
    }

    @Test
    void testTokenConsumedBeforeCall() {
        // Create a bucket with 10 tokens
        bucket = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(10).refillGreedy(1, Duration.ofSeconds(1)).build())
                .build();

        FlowControlInterceptor interceptor = new FlowControlInterceptor(bucket, 5);

        long initialTokens = bucket.getAvailableTokens();

        ClientCall<Object, Object> interceptedCall = interceptor.interceptCall(
                methodDescriptor, CallOptions.DEFAULT, channel);

        // Start the call (this consumes a token)
        interceptedCall.start(mock(ClientCall.Listener.class), new Metadata());

        // Verify a token was consumed
        assertEquals(initialTokens - 1, bucket.getAvailableTokens());
    }

    @Test
    void testBackpressurePenalizesTokenBucket() {
        // Create a bucket with 20 tokens
        bucket = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(20).refillGreedy(1, Duration.ofSeconds(1)).build())
                .build();

        int backpressurePenalty = 5;
        FlowControlInterceptor interceptor = new FlowControlInterceptor(bucket, backpressurePenalty);

        ClientCall<Object, Object> interceptedCall = interceptor.interceptCall(
                methodDescriptor, CallOptions.DEFAULT, channel);

        // Capture the listener passed to the real call
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ClientCall.Listener<Object>> listenerCaptor = ArgumentCaptor.forClass(ClientCall.Listener.class);

        interceptedCall.start(mock(ClientCall.Listener.class), new Metadata());

        verify(clientCall).start(listenerCaptor.capture(), any(Metadata.class));

        // Get the wrapped listener and simulate a RESOURCE_EXHAUSTED response
        ClientCall.Listener<Object> wrappedListener = listenerCaptor.getValue();
        long tokensBeforeBackpressure = bucket.getAvailableTokens();

        // Simulate backpressure
        wrappedListener.onClose(Status.RESOURCE_EXHAUSTED, new Metadata());

        // Verify penalty was applied (5 penalty tokens consumed)
        assertEquals(tokensBeforeBackpressure - backpressurePenalty, bucket.getAvailableTokens());
    }

    @Test
    void testNormalResponseDoesNotPenalizeBucket() {
        // Create a bucket with 20 tokens
        bucket = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(20).refillGreedy(1, Duration.ofSeconds(1)).build())
                .build();

        FlowControlInterceptor interceptor = new FlowControlInterceptor(bucket, 10);

        ClientCall<Object, Object> interceptedCall = interceptor.interceptCall(
                methodDescriptor, CallOptions.DEFAULT, channel);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ClientCall.Listener<Object>> listenerCaptor = ArgumentCaptor.forClass(ClientCall.Listener.class);

        interceptedCall.start(mock(ClientCall.Listener.class), new Metadata());

        verify(clientCall).start(listenerCaptor.capture(), any(Metadata.class));

        ClientCall.Listener<Object> wrappedListener = listenerCaptor.getValue();
        long tokensAfterStart = bucket.getAvailableTokens();

        // Simulate a successful response
        wrappedListener.onClose(Status.OK, new Metadata());

        // Verify no additional tokens were consumed
        assertEquals(tokensAfterStart, bucket.getAvailableTokens());
    }

    @Test
    void testRateLimitingWithMultipleRequests() throws InterruptedException {
        // Create a bucket that allows only 2 tokens with slow refill
        bucket = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(2).refillGreedy(1, Duration.ofSeconds(10)).build())
                .build();

        FlowControlInterceptor interceptor = new FlowControlInterceptor(bucket, 1);

        AtomicInteger completedCalls = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);

        // Start 3 threads trying to make calls
        for (int i = 0; i < 3; i++) {
            Thread.startVirtualThread(() -> {
                ClientCall<Object, Object> call = interceptor.interceptCall(
                        methodDescriptor, CallOptions.DEFAULT, channel);
                call.start(mock(ClientCall.Listener.class), new Metadata());
                completedCalls.incrementAndGet();
                latch.countDown();
            });
        }

        // Wait for 100ms - only 2 calls should complete (bucket has 2 tokens)
        boolean allCompleted = latch.await(100, TimeUnit.MILLISECONDS);

        // Not all should complete because third request is waiting for token
        assertFalse(allCompleted);
        assertEquals(2, completedCalls.get());
    }

    @Test
    void testBackpressurePassesErrorToOriginalListener() throws InterruptedException {
        bucket = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(100).refillGreedy(50, Duration.ofMillis(100)).build())
                .build();

        FlowControlInterceptor interceptor = new FlowControlInterceptor(bucket, 2);

        when(channel.newCall(any(), any())).thenReturn(clientCall);

        ClientCall<Object, Object> interceptedCall = interceptor.interceptCall(
                methodDescriptor, CallOptions.DEFAULT, channel);

        CountDownLatch errorReceived = new CountDownLatch(1);

        // Create a listener that tracks when it receives the error
        @SuppressWarnings("unchecked")
        ClientCall.Listener<Object> originalListener = mock(ClientCall.Listener.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            errorReceived.countDown();
            return null;
        }).when(originalListener).onClose(any(Status.class), any(Metadata.class));

        interceptedCall.start(originalListener, new Metadata());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ClientCall.Listener<Object>> listenerCaptor = ArgumentCaptor.forClass(ClientCall.Listener.class);
        verify(clientCall).start(listenerCaptor.capture(), any(Metadata.class));

        // Simulate RESOURCE_EXHAUSTED - should pass directly to original listener
        listenerCaptor.getValue().onClose(Status.RESOURCE_EXHAUSTED, new Metadata());

        // Should receive error immediately
        boolean received = errorReceived.await(100, TimeUnit.MILLISECONDS);
        assertTrue(received, "Error should be passed to original listener");

        // Verify only one call was made (no retry)
        verify(channel, times(1)).newCall(any(), any());
    }
}
