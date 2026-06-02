package com.buyology.ecommerce.infrastructure.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Provides a bounded executor for {@code @Async} work.
 *
 * <p>{@code @EnableAsync} is declared on the main application class. Without an
 * explicit executor, Spring falls back to an unbounded {@code SimpleAsyncTaskExecutor}
 * that spawns a new thread per task. This configuration replaces that with a
 * bounded {@link ThreadPoolTaskExecutor} so async work cannot exhaust resources.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-");
        // When the pool and queue are saturated, run the task on the caller's
        // thread to apply back-pressure instead of dropping or queueing forever.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}
