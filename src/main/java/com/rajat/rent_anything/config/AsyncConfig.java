package com.rajat.rent_anything.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous task execution.
 *
 * Enables Spring's @Async support and provides
 * a dedicated thread pool for email delivery tasks.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Number of threads always kept alive
        executor.setCorePoolSize(5);

        // Maximum number of threads that can be created
        executor.setMaxPoolSize(20);

        // Number of queued email tasks before new threads are created
        executor.setQueueCapacity(100);

        executor.setThreadNamePrefix("email-");

        executor.initialize();

        return executor;
    }
}