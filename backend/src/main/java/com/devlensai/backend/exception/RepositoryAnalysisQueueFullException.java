package com.devlensai.backend.exception;

public class RepositoryAnalysisQueueFullException extends RuntimeException {
    public RepositoryAnalysisQueueFullException() { super("Local repository analysis queue is full; retry later"); }
}
