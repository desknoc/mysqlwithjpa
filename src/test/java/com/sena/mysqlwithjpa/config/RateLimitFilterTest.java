package com.sena.mysqlwithjpa.config;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Threat-matrix rate-limit coverage: a global per-IP bucket4j limit, exceeded
 * requests answered with HTTP 429 in the ApiError envelope shape plus a
 * Retry-After header, X-Forwarded-For clients behind one NAT mapped to
 * independent buckets, and any internal filter failure failing OPEN (never a
 * 500 for the underlying request). Runs as a plain unit slice — no Docker.
 */
class RateLimitFilterTest {

    private static final String REMOTE_ADDR = "192.168.0.10";

    @Test
    void requestsBeyondCapacityGet429WithApiErrorEnvelopeAndRetryAfter() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 120);

        PassThrough first = run(filter, request("/api/users", REMOTE_ADDR, null));
        PassThrough second = run(filter, request("/api/users", REMOTE_ADDR, null));
        assertThat(first.chainInvoked()).isTrue();
        assertThat(second.chainInvoked()).isTrue();

        PassThrough exceeded = run(filter, request("/api/users", REMOTE_ADDR, null));
        assertThat(exceeded.chainInvoked())
                .as("an exceeded request never reaches the application")
                .isFalse();
        assertThat(exceeded.response().getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(exceeded.response().getHeader(HttpHeaders.RETRY_AFTER))
                .as("429 responses must carry a Retry-After header with seconds until refill")
                .isNotNull();
        assertThat(Long.parseLong(exceeded.response().getHeader(HttpHeaders.RETRY_AFTER)))
                .isGreaterThanOrEqualTo(1);
        assertThat(exceeded.response().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(exceeded.response().getContentAsString())
                .contains("\"status\":429")
                .contains("\"error\":\"Too Many Requests\"")
                .contains("\"message\":\"Too many requests\"")
                .contains("\"path\":\"/api/users\"");
    }

    @Test
    void bucketIsGlobalAcrossEndpointsNotPerEndpoint() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 120);

        run(filter, request("/api/users", REMOTE_ADDR, null));
        run(filter, request("/api/users/search", REMOTE_ADDR, null));

        PassThrough third = run(filter, request("/api/anything", REMOTE_ADDR, null));
        assertThat(third.response().getStatus())
                .as("requests to ANY endpoint share one bucket for the IP")
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void xForwardedForClientsBehindOneNatGetIndependentBuckets() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 120);

        run(filter, request("/api/users", REMOTE_ADDR, "10.1.1.1"));
        run(filter, request("/api/users", REMOTE_ADDR, "10.1.1.1"));
        PassThrough firstClientExceeded =
                run(filter, request("/api/users", REMOTE_ADDR, "10.1.1.1"));
        assertThat(firstClientExceeded.response().getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

        PassThrough secondClient =
                run(filter, request("/api/users", REMOTE_ADDR, "10.2.2.2, 172.16.0.1"));
        assertThat(secondClient.response().getStatus())
                .as("a different first X-Forwarded-For value owns its own bucket")
                .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(secondClient.chainInvoked()).isTrue();

        PassThrough bareRemote =
                run(filter, request("/api/users", REMOTE_ADDR, null));
        assertThat(bareRemote.chainInvoked())
                .as("clients without X-Forwarded-For are keyed by the remote address")
                .isTrue();
    }

    @Test
    void internalLimiterFailureFailsOpenNever500() throws Exception {
        ConcurrentMap<String, Bucket> brokenBuckets = new ConcurrentHashMap<>() {
            @Override
            public Bucket computeIfAbsent(String key,
                    Function<? super String, ? extends Bucket> mappingFunction) {
                throw new IllegalStateException("simulated limiter failure");
            }
        };
        RateLimitFilter filter = new RateLimitFilter(2, 120, brokenBuckets);

        PassThrough result = run(filter, request("/api/users", REMOTE_ADDR, null));
        assertThat(result.chainInvoked())
                .as("limiter failures must not block the underlying request")
                .isTrue();
        assertThat(result.response().getStatus())
                .as("limiter failures must never surface as HTTP 500")
                .isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    private MockHttpServletRequest request(String path, String remoteAddr, String xForwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(remoteAddr);
        if (xForwardedFor != null) {
            request.addHeader("X-Forwarded-For", xForwardedFor);
        }
        return request;
    }

    private PassThrough run(RateLimitFilter filter, MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (ServletRequest req, ServletResponse res) -> chainInvoked.set(true);
        filter.doFilter(request, response, chain);
        return new PassThrough(response, chainInvoked.get());
    }

    private record PassThrough(MockHttpServletResponse response, boolean chainInvoked) {
    }
}
