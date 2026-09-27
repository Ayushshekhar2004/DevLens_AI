package com.devlensai.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record RepositoryRetrievalProperties(int graphDepth, int candidateLimit, int maxSnippets,
        int perFileTokens, int contextTokens, int instructionReserveTokens,
        int outputReserveTokens, int safetyReserveTokens, int lineWindow) {
    public RepositoryRetrievalProperties(
            @Value("${app.repository-retrieval.graph-depth}") int graphDepth,
            @Value("${app.repository-retrieval.candidate-limit}") int candidateLimit,
            @Value("${app.repository-retrieval.max-snippets}") int maxSnippets,
            @Value("${app.repository-retrieval.per-file-tokens}") int perFileTokens,
            @Value("${app.repository-retrieval.context-tokens}") int contextTokens,
            @Value("${app.repository-retrieval.instruction-reserve-tokens}") int instructionReserveTokens,
            @Value("${app.repository-retrieval.output-reserve-tokens}") int outputReserveTokens,
            @Value("${app.repository-retrieval.safety-reserve-tokens}") int safetyReserveTokens,
            @Value("${app.repository-retrieval.line-window}") int lineWindow) {
        int usable = contextTokens - instructionReserveTokens - outputReserveTokens - safetyReserveTokens;
        if (graphDepth < 0 || graphDepth > 5 || candidateLimit < 1 || candidateLimit > 500
                || maxSnippets < 1 || maxSnippets > 100 || perFileTokens < 64 || perFileTokens > 8192
                || contextTokens < 1024 || instructionReserveTokens < 128 || outputReserveTokens < 128
                || safetyReserveTokens < 64 || usable < 128 || lineWindow < 1 || lineWindow > 200) {
            throw new IllegalArgumentException("Invalid repository retrieval limits");
        }
        this.graphDepth = graphDepth; this.candidateLimit = candidateLimit; this.maxSnippets = maxSnippets;
        this.perFileTokens = perFileTokens; this.contextTokens = contextTokens;
        this.instructionReserveTokens = instructionReserveTokens; this.outputReserveTokens = outputReserveTokens;
        this.safetyReserveTokens = safetyReserveTokens; this.lineWindow = lineWindow;
    }
    public int usableContextTokens() { return contextTokens - instructionReserveTokens - outputReserveTokens - safetyReserveTokens; }
}
