package com.devlensai.backend.exception;

public class RepositoryScanException extends RuntimeException {
    public RepositoryScanException(String message) { super(message); }
    public RepositoryScanException(String message, Throwable cause) { super(message, cause); }
}
