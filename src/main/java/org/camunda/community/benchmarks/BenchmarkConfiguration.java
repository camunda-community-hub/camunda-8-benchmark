package org.camunda.community.benchmarks;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@ConfigurationProperties(prefix = "benchmark")
public class BenchmarkConfiguration {

    private long startPiPerSecond = 1;

    public long getStartPiPerSecond() {
        return startPiPerSecond;
    }

    public void setStartPiPerSecond(long startPiPerSecond) {
        this.startPiPerSecond = startPiPerSecond;
    }
}
