package com.truthguard.exception;

public class AiServiceTimeoutException extends RuntimeException {
    public AiServiceTimeoutException(String message) {
        super(message);
    }
    public AiServiceTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
