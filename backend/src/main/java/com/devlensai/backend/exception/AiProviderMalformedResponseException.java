package com.devlensai.backend.exception;

public class AiProviderMalformedResponseException extends AiProviderException {

    public AiProviderMalformedResponseException(String message) {
        super(message);
    }

    public AiProviderMalformedResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
