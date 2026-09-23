package com.devlensai.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record RepositoryLifecycleProperties(int retentionDays, long cleanupIntervalHours) {
    public RepositoryLifecycleProperties(
            @Value("${app.repository-lifecycle.retention-days}") int retentionDays,
            @Value("${app.repository-lifecycle.cleanup-interval-hours}") long cleanupIntervalHours) {
        if (retentionDays < 0 || cleanupIntervalHours < 1) throw new IllegalArgumentException("Invalid retention policy");
        this.retentionDays = retentionDays;
        this.cleanupIntervalHours = cleanupIntervalHours;
    }
}
