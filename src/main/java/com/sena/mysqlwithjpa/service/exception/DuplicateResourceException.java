package com.sena.mysqlwithjpa.service.exception;

/**
 * Raised when a uniqueness pre-check (or constraint) detects a conflicting
 * value. Carries the conflicting field name so the HTTP 409 envelope can
 * identify it to the client (mapped in {@code ExceptionController}).
 */
public class DuplicateResourceException extends RuntimeException {

    private final String field;

    public DuplicateResourceException(String field, Object value) {
        super("Duplicate value for '" + field + "': " + value);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
