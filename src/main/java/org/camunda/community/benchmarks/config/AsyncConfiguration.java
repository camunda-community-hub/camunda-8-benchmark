package org.camunda.community.benchmarks.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import io.camunda.zeebe.spring.client.actuator.MicrometerMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AsyncConfiguration {

    private static final Logger LOGGER = LogManager.getLogger(AsyncConfiguration.class);
    
    @Value("${async.corePoolSize}")
    private int corePoolSize;
    
    @Value("${async.maxPoolSize}")
    private int maxPoolSize;
    
    @Value("${async.queueCapacity}")
    private int queueCapacity;

    @Value("${scheduler.poolSize}")
    private int schedulerPoolSize;

    /**
     * Executor to run everything that is @Async
     */
    @Bean
    public Executor taskExecutor() {
        LOGGER.debug("Creating Async Task Executor");
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setThreadNamePrefix("AsyncThread-");
        executor.initialize();
        return executor;
    }

    /**
     * Executor to run everything that is scheduled (also @Scheduled)
     */
    @Bean
    public TaskScheduler taskScheduler() {
        LOGGER.debug("Creating Async Task Scheduler");
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(corePoolSize);
        scheduler.setThreadNamePrefix("ThreadPoolTaskScheduler-");
        scheduler.initialize();
        return scheduler;
    }
    
    @Bean
    public ScheduledExecutorService scheduledExecutorService() {
        ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);
        return ses;
    }
}
