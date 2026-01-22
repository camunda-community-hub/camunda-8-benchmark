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
package org.camunda.community.benchmarks.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.grpc.ClientInterceptor;
import org.camunda.community.benchmarks.flowcontrol.FlowControlInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for flow control using Bucket4j.
 * <p>
 * When enabled, this configuration creates a gRPC interceptor that rate limits
 * outgoing calls to Zeebe/Camunda. The interceptor uses a token bucket algorithm
 * to control the rate of requests and automatically handles backpressure by
 * penalizing the bucket when RESOURCE_EXHAUSTED errors are received.
 */
@Configuration
public class FlowControlConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(FlowControlConfiguration.class);

    @Bean
    @ConditionalOnProperty(name = "benchmark.flowControlEnabled", havingValue = "true")
    public Bucket flowControlBucket(BenchmarkConfiguration config) {
        LOG.info("Creating flow control bucket with capacity={}, refillTokens={}, refillPeriodMs={}",
                config.getFlowControlCapacity(),
                config.getFlowControlRefillTokens(),
                config.getFlowControlRefillPeriodMs());

        Bandwidth limit = Bandwidth.builder()
                .capacity(config.getFlowControlCapacity())
                .refillGreedy(config.getFlowControlRefillTokens(),
                        Duration.ofMillis(config.getFlowControlRefillPeriodMs()))
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "benchmark.flowControlEnabled", havingValue = "true")
    public ClientInterceptor flowControlInterceptor(Bucket flowControlBucket, BenchmarkConfiguration config) {
        LOG.info("Creating flow control interceptor with backpressurePenalty={}",
                config.getFlowControlBackpressurePenalty());

        return new FlowControlInterceptor(
                flowControlBucket,
                config.getFlowControlBackpressurePenalty());
    }
}
