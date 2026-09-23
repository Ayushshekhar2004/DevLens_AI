package com.devlensai.backend.exception;

public class RepositoryImportException extends RuntimeException {
    public RepositoryImportException(String message) { super(message); }
    public RepositoryImportException(String message, Throwable cause) { super(message, cause); }
}
