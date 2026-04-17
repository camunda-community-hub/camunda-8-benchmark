package org.camunda.community.benchmarks.flowcontrol;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.camunda.community.benchmarks.StatisticsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

    @Mock
    private StatisticsCollector stats;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(channel.newCall(any(), any())).thenReturn(clientCall);
        when(methodDescriptor.getFullMethodName()).thenReturn("test/method");
    }

    @Test
    void testResourceExhaustedPenalizesBucket() {
        bucket = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(100).refillGreedy(1, Duration.ofSeconds(10)).build())
                .build();

        long penaltyTokens = 10;
        FlowControlInterceptor interceptor = new FlowControlInterceptor(bucket,penaltyTokens, stats);

        ClientCall<Object, Object> interceptedCall = interceptor.interceptCall(
                methodDescriptor, CallOptions.DEFAULT, channel);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ClientCall.Listener<Object>> listenerCaptor = ArgumentCaptor.forClass(ClientCall.Listener.class);
        interceptedCall.start(mock(ClientCall.Listener.class), new Metadata());
        verify(clientCall).start(listenerCaptor.capture(), any(Metadata.class));

        long tokensBefore = bucket.getAvailableTokens();
        listenerCaptor.getValue().onClose(Status.RESOURCE_EXHAUSTED, new Metadata());

        assertEquals(tokensBefore - penaltyTokens, bucket.getAvailableTokens());
    }

    @Test
    void testOkResponseDoesNotPenalizeBucket() {
        bucket = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(100).refillGreedy(1, Duration.ofSeconds(10)).build())
                .build();

        FlowControlInterceptor interceptor = new FlowControlInterceptor(bucket,10, stats);

        ClientCall<Object, Object> interceptedCall = interceptor.interceptCall(
                methodDescriptor, CallOptions.DEFAULT, channel);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ClientCall.Listener<Object>> listenerCaptor = ArgumentCaptor.forClass(ClientCall.Listener.class);
        interceptedCall.start(mock(ClientCall.Listener.class), new Metadata());
        verify(clientCall).start(listenerCaptor.capture(), any(Metadata.class));

        long tokensBefore = bucket.getAvailableTokens();
        listenerCaptor.getValue().onClose(Status.OK, new Metadata());

        assertEquals(tokensBefore, bucket.getAvailableTokens());
    }

    @Test
    void testBackpressureCounterIncremented() {
        bucket = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(100).refillGreedy(1, Duration.ofSeconds(10)).build())
                .build();

        FlowControlInterceptor interceptor = new FlowControlInterceptor(bucket,5, stats);
        assertEquals(0, interceptor.getBackpressureCount().get());

        ClientCall<Object, Object> call = interceptor.interceptCall(
                methodDescriptor, CallOptions.DEFAULT, channel);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ClientCall.Listener<Object>> captor = ArgumentCaptor.forClass(ClientCall.Listener.class);
        call.start(mock(ClientCall.Listener.class), new Metadata());
        verify(clientCall).start(captor.capture(), any(Metadata.class));

        captor.getValue().onClose(Status.RESOURCE_EXHAUSTED, new Metadata());

        assertEquals(1, interceptor.getBackpressureCount().get());
    }

    @Test
    void testTotalCallCounterIncremented() {
        bucket = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(100).refillGreedy(1, Duration.ofSeconds(10)).build())
                .build();

        FlowControlInterceptor interceptor = new FlowControlInterceptor(bucket,5, stats);
        assertEquals(0, interceptor.getTotalCallCount().get());

        ClientCall<Object, Object> call = interceptor.interceptCall(
                methodDescriptor, CallOptions.DEFAULT, channel);
        call.start(mock(ClientCall.Listener.class), new Metadata());

        assertEquals(1, interceptor.getTotalCallCount().get());
    }

    @Test
    void testStatusPassedToOriginalListener() {
        bucket = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(100).refillGreedy(1, Duration.ofSeconds(10)).build())
                .build();

        FlowControlInterceptor interceptor = new FlowControlInterceptor(bucket,5, stats);

        ClientCall<Object, Object> interceptedCall = interceptor.interceptCall(
                methodDescriptor, CallOptions.DEFAULT, channel);

        @SuppressWarnings("unchecked")
        ClientCall.Listener<Object> originalListener = mock(ClientCall.Listener.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ClientCall.Listener<Object>> listenerCaptor = ArgumentCaptor.forClass(ClientCall.Listener.class);
        interceptedCall.start(originalListener, new Metadata());
        verify(clientCall).start(listenerCaptor.capture(), any(Metadata.class));

        Metadata trailers = new Metadata();
        listenerCaptor.getValue().onClose(Status.RESOURCE_EXHAUSTED, trailers);

        verify(originalListener).onClose(Status.RESOURCE_EXHAUSTED, trailers);
    }
}
