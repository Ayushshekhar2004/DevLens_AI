package com.devlensai.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class RepositoryLifecycleConfig {
    @Bean("repositoryTaskExecutor")
    ThreadPoolTaskExecutor repositoryTaskExecutor(
            @Value("${app.repository-lifecycle.worker-count}") int workers,
            @Value("${app.repository-lifecycle.queue-capacity}") int queueCapacity) {
        if (workers < 1 || workers > 8 || queueCapacity < 1 || queueCapacity > 1000) {
            throw new IllegalArgumentException("Invalid repository lifecycle executor limits");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("repository-scan-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
