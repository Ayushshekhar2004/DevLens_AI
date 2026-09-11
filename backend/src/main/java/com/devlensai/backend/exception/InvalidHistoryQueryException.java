package com.devlensai.backend.exception;

public class InvalidHistoryQueryException extends RuntimeException {

    public InvalidHistoryQueryException(String message) {
        super(message);
    }
}
