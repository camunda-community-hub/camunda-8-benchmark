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

import java.util.concurrent.atomic.AtomicLong;

/**
 * A gRPC client interceptor that provides backpressure feedback to a Bucket4j token bucket.
 * <p>
 * When a {@code RESOURCE_EXHAUSTED} status is received from Zeebe, this interceptor penalizes
 * the bucket by consuming additional tokens. This creates a feedback loop that automatically
 * slows down virtual threads waiting for tokens in the {@link Bucket4jPiScheduler}.
 * <p>
 * This interceptor is registered on the whole {@code CamundaClient} (every gRPC call, not just
 * PI starts), so the immediate bucket penalty below fires for backpressure on any call type, not
 * just PI starts. It does <b>not</b> forward to {@code StatisticsCollector}'s {@code pi_backpressure}
 * meter — that's already populated, PI-start-specific and without double-counting, by
 * {@code BenchmarkStartPiExceptionHandlingStrategy} (which runs regardless of which
 * {@code startRateAdjustmentStrategy} is active). {@link Bucket4jPiScheduler#adjustRate()} reads
 * that same meter for its periodic decisions, rather than this interceptor's own counters below.
 */
public class FlowControlInterceptor implements ClientInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(FlowControlInterceptor.class);

    private final Bucket bucket;
    private final RateGoal rate;
    private final double penaltyFactor;
    private final AtomicLong backpressureCount = new AtomicLong(0);
    private final AtomicLong totalCallCount = new AtomicLong(0);

    public FlowControlInterceptor(Bucket bucket, RateGoal rate, double penaltyFactor) {
        this.bucket = bucket;
        this.rate = rate;
        this.penaltyFactor = penaltyFactor;
    }

    public AtomicLong getBackpressureCount() {
        return backpressureCount;
    }

    public AtomicLong getTotalCallCount() {
        return totalCallCount;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                totalCallCount.incrementAndGet();
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        if (status.getCode() == Status.Code.RESOURCE_EXHAUSTED) {
                            // Recomputed from the live rate (not captured once at startup) so the
                            // penalty stays proportional as autoTune raises/lowers currentRate and
                            // the bucket's capacity along with it.
                            long penaltyTokens = Math.max(1, Math.round(rate.get() * penaltyFactor));
                            bucket.consumeIgnoringRateLimits(penaltyTokens);
                            backpressureCount.incrementAndGet();
                            LOG.debug("Backpressure detected for {}. Penalized bucket by {} tokens.",
                                    method.getFullMethodName(), penaltyTokens);
                        }
                        super.onClose(status, trailers);
                    }
                }, headers);
            }
        };
    }
}
