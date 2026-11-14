package com.maheshshinde.CryptEnv.provider;

/** Thrown when a provider interaction (health check, list, push, delete) fails. */
public class ProviderException extends RuntimeException {

    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
