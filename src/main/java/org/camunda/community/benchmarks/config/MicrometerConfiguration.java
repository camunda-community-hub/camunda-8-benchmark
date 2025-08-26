package org.camunda.community.benchmarks.config;

import io.camunda.zeebe.spring.client.actuator.MicrometerMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MicrometerConfiguration {

    @Bean
    public MicrometerMetricsRecorder micrometerMetricsRecorder(MeterRegistry meterRegistry) {
        return new MicrometerMetricsRecorder(meterRegistry);
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> {
            // This customizer runs early in the lifecycle, before any meters are registered
            // Apply any custom MeterFilters here if needed in the future
        };
    }
}

