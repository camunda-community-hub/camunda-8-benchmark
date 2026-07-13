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

import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared, mutable view of the current flow-control target rate (PI/s).
 * <p>
 * Both {@link Bucket4jPiScheduler} (which adjusts the rate under the {@code autoTune}
 * strategy) and {@link FlowControlInterceptor} (which needs the rate to size its
 * backpressure penalty proportionally) depend on this bean instead of on each other,
 * avoiding a circular dependency between the two.
 */
public class FlowControlRate {

    private final AtomicLong currentRate;

    public FlowControlRate(long initialRate) {
        this.currentRate = new AtomicLong(initialRate);
    }

    public long get() {
        return currentRate.get();
    }

    public void set(long rate) {
        currentRate.set(rate);
    }
}
