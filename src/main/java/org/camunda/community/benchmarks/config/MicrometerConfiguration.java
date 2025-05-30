package org.camunda.community.benchmarks.config;

import io.camunda.zeebe.spring.client.actuator.MicrometerMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MicrometerConfiguration {

    @Autowired
    private MeterRegistry meterRegistry;

    @Bean
    public MicrometerMetricsRecorder micrometerMetricsRecorder() {
        return new MicrometerMetricsRecorder(meterRegistry);
    }
}

