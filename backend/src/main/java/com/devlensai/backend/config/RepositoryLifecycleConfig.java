package com.devlensai.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    @Bean("repositoryAnalysisExecutor")
    ThreadPoolTaskExecutor repositoryAnalysisExecutor(RepositoryAnalysisProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workerCount());
        executor.setMaxPoolSize(properties.workerCount());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("repository-analysis-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    @Bean(name = "localInferenceExecutor", destroyMethod = "shutdownNow")
    ExecutorService localInferenceExecutor(RepositoryAnalysisProperties properties) {
        return Executors.newFixedThreadPool(properties.inferenceConcurrency(),
                Thread.ofPlatform().name("local-inference-", 0).factory());
    }
}
