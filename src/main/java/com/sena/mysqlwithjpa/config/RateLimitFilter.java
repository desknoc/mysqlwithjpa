package com.sena.mysqlwithjpa.config;

import com.sena.mysqlwithjpa.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Global per-IP rate limit (design Decision 3, api-security spec): one bucket4j
 * bucket per client key, shared across the whole API surface. The client key
 * is the first {@code X-Forwarded-For} value when present, otherwise the
 * remote address.
 *
 * On exhaustion the filter throws {@link RateLimitExceededException} and
 * renders the 429 ApiError-shaped envelope itself directly — servlet filters
 * run before the DispatcherServlet, so {@code @RestControllerAdvice} cannot
 * see exceptions raised here. {@code Retry-After} carries seconds until one
 * token refills.
 *
 * Any internal limiter failure fails OPEN: the request is let through and the
 * problem is logged — a broken rate limiter must never 500 the application.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final long capacity;
    private final long refillPerMinute;
    private final ConcurrentMap<String, Bucket> buckets;

    public RateLimitFilter(long capacity, long refillPerMinute) {
        this(capacity, refillPerMinute, new ConcurrentHashMap<>());
    }

    public RateLimitFilter(long capacity, long refillPerMinute,
            ConcurrentMap<String, Bucket> buckets) {
        this.capacity = capacity;
        this.refillPerMinute = refillPerMinute;
        this.buckets = buckets;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            consumeTokenOrThrow(clientKey(request));
        } catch (RateLimitExceededException ex) {
            log.warn("Rate limit exceeded for {} on {}", clientKey(request), request.getRequestURI());
            writeTooManyRequests(request, response, ex.getRetryAfterSeconds());
            return;
        } catch (RuntimeException ex) {
            // Fail open: a broken limiter must never block or 500 the request.
            log.error("Rate limiter failure; letting the request through", ex);
        }
        filterChain.doFilter(request, response);
    }

    private void consumeTokenOrThrow(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new RateLimitExceededException(retryAfterSeconds(probe));
        }
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(refillPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private long retryAfterSeconds(ConsumptionProbe probe) {
        return Math.max(1L, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
    }

    /**
     * Renders the same envelope shape {@code ApiError} produces for controller
     * exceptions. LocalDateTime ISO output and a minimal string escape keep
     * this free of Jackson configuration at the filter layer.
     */
    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response,
            long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.getWriter().write("{"
                + "\"timestamp\":\"" + LocalDateTime.now() + "\","
                + "\"status\":" + HttpStatus.TOO_MANY_REQUESTS.value() + ","
                + "\"error\":\"" + HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase() + "\","
                + "\"message\":\"Too many requests\","
                + "\"path\":\"" + escapeJson(request.getRequestURI()) + "\""
                + "}");
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
