package com.sena.mysqlwithjpa.exception;

/**
 * Raised when a client exceeds its per-IP rate-limit bucket. Carries the
 * seconds until one token refills so the 429 response can set a meaningful
 * {@code Retry-After} header. Mapped to HTTP 429.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Too many requests");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
