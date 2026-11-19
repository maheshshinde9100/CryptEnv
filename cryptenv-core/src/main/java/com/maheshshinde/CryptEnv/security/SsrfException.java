package com.maheshshinde.CryptEnv.security;

/** Thrown when an outbound URL fails anti-SSRF validation (§10). Maps to HTTP 400. */
public class SsrfException extends RuntimeException {

    public SsrfException(String message) {
        super(message);
    }
}
