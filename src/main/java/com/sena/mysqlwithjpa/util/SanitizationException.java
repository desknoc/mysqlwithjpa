package com.sena.mysqlwithjpa.util;

/**
 * Raised when an input string carries a rejected payload (XSS-shaped markup,
 * event-handler attributes, path traversal). The global exception handler
 * maps this to HTTP 400 (wired in the rest-api-redesign Phase 4 change).
 */
public class SanitizationException extends RuntimeException {

    public SanitizationException(String field, String reason) {
        super("Invalid value for '" + field + "': " + reason);
    }
}
