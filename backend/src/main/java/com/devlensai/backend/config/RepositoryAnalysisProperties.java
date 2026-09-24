package com.devlensai.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record RepositoryAnalysisProperties(
        int workerCount, int queueCapacity, int inferenceConcurrency,
        long callTimeoutSeconds, long jobDeadlineSeconds,
        int maxCalls, int maxInputTokens, int maxOutputTokens, int maxFiles,
        int contextTokens, int systemReserveTokens, int schemaReserveTokens,
        int outputReserveTokens, int safetyReserveTokens, int overlapLines) {

    public RepositoryAnalysisProperties(
            @Value("${app.repository-analysis.worker-count}") int workerCount,
            @Value("${app.repository-analysis.queue-capacity}") int queueCapacity,
            @Value("${app.repository-analysis.inference-concurrency}") int inferenceConcurrency,
            @Value("${app.repository-analysis.call-timeout-seconds}") long callTimeoutSeconds,
            @Value("${app.repository-analysis.job-deadline-seconds}") long jobDeadlineSeconds,
            @Value("${app.repository-analysis.max-calls}") int maxCalls,
            @Value("${app.repository-analysis.max-input-tokens}") int maxInputTokens,
            @Value("${app.repository-analysis.max-output-tokens}") int maxOutputTokens,
            @Value("${app.repository-analysis.max-files}") int maxFiles,
            @Value("${app.repository-analysis.context-tokens}") int contextTokens,
            @Value("${app.repository-analysis.system-reserve-tokens}") int systemReserveTokens,
            @Value("${app.repository-analysis.schema-reserve-tokens}") int schemaReserveTokens,
            @Value("${app.repository-analysis.output-reserve-tokens}") int outputReserveTokens,
            @Value("${app.repository-analysis.safety-reserve-tokens}") int safetyReserveTokens,
            @Value("${app.repository-analysis.overlap-lines}") int overlapLines) {
        int usable = contextTokens - systemReserveTokens - schemaReserveTokens - outputReserveTokens - safetyReserveTokens;
        if (workerCount < 1 || workerCount > 4 || queueCapacity < 1 || queueCapacity > 100
                || inferenceConcurrency < 1 || inferenceConcurrency > 4 || callTimeoutSeconds < 1
                || callTimeoutSeconds > 600 || jobDeadlineSeconds < callTimeoutSeconds
                || jobDeadlineSeconds > 86_400 || maxCalls < 1 || maxCalls > 10_000
                || maxInputTokens < 128 || maxOutputTokens < outputReserveTokens
                || maxFiles < 1 || maxFiles > 10_000
                || contextTokens < 1024 || outputReserveTokens < 128 || outputReserveTokens > 8192
                || usable < 128 || overlapLines < 0 || overlapLines > 50) {
            throw new IllegalArgumentException("Invalid repository analysis limits");
        }
        this.workerCount = workerCount;
        this.queueCapacity = queueCapacity;
        this.inferenceConcurrency = inferenceConcurrency;
        this.callTimeoutSeconds = callTimeoutSeconds;
        this.jobDeadlineSeconds = jobDeadlineSeconds;
        this.maxCalls = maxCalls;
        this.maxInputTokens = maxInputTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.maxFiles = maxFiles;
        this.contextTokens = contextTokens;
        this.systemReserveTokens = systemReserveTokens;
        this.schemaReserveTokens = schemaReserveTokens;
        this.outputReserveTokens = outputReserveTokens;
        this.safetyReserveTokens = safetyReserveTokens;
        this.overlapLines = overlapLines;
    }

    public int usableInputTokens() {
        return contextTokens - systemReserveTokens - schemaReserveTokens - outputReserveTokens - safetyReserveTokens;
    }
}
