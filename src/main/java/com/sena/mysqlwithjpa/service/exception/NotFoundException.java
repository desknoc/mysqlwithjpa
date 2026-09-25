package com.sena.mysqlwithjpa.service.exception;

/**
 * Raised when a resource addressed by identifier does not exist.
 * Mapped to HTTP 404 in {@code ExceptionController}.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super(resource + " not found: " + id);
    }
}
