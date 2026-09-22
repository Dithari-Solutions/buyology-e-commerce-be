package com.buyology.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Gives the {@code @Scheduled} jobs a thread pool of their own.
 *
 * <p>They were all running on ONE thread. WebSocketConfig declares the STOMP heartbeat scheduler
 * as the {@code @Primary} {@code TaskScheduler} with a pool of one, and Spring's scheduled-task
 * processor picks the primary scheduler; Spring Boot builds its own only when none exists. So
 * {@code spring.task.scheduling.pool.size=4} in the production config looked applied and did
 * nothing, and every job — the payment settlement sweep, the Quiqup retries, the stock and refund
 * reconcilers — queued behind the one before it. One slow outbound call held up all of them.
 *
 * <p>Registered through {@link SchedulingConfigurer}, so the heartbeat keeps its own scheduler and
 * nothing else changes. Each job still never overlaps with itself. A bean rather than a local, so
 * the container shuts it down on stop and an in-flight job gets its 20 seconds to finish; it is not
 * {@code @Primary}, so anything injecting a {@code TaskScheduler} still gets the heartbeat one.
 */
@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    private final int poolSize;

    public SchedulingConfig(@Value("${spring.task.scheduling.pool.size:4}") int poolSize) {
        this.poolSize = Math.max(1, poolSize);
    }

    @Bean
    public ThreadPoolTaskScheduler scheduledJobsScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(scheduledJobsScheduler());
    }
}
