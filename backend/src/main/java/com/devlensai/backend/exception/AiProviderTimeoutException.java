package com.devlensai.backend.exception;

public class AiProviderTimeoutException extends AiProviderException {

    public AiProviderTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
