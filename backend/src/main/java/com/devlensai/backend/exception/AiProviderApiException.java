package com.devlensai.backend.exception;

public class AiProviderApiException extends AiProviderException {

    public AiProviderApiException(String message) {
        super(message);
    }

    public AiProviderApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
