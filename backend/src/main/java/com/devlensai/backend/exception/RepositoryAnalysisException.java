package com.devlensai.backend.exception;

public class RepositoryAnalysisException extends RuntimeException {
    public RepositoryAnalysisException(String message) { super(message); }
    public RepositoryAnalysisException(String message, Throwable cause) { super(message, cause); }
}
