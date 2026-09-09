package com.devlensai.backend.exception;

public abstract class AiProviderException extends RuntimeException {

    protected AiProviderException(String message) {
        super(message);
    }

    protected AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
