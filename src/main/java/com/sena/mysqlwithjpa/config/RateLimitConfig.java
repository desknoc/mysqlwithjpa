package com.sena.mysqlwithjpa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Binds {@code app.ratelimit.*} (environment-driven per design Decision 10:
 * {@code RATE_LIMIT_CAPACITY} / {@code RATE_LIMIT_REFILL_PER_MINUTE}, both
 * defaulting to 100) and registers the {@link RateLimitFilter} for
 * {@code /api/**} ahead of the security chain.
 */
@Configuration
@ConfigurationProperties(prefix = "app.ratelimit")
public class RateLimitConfig {

    /** Maximum tokens a client bucket holds. */
    private long capacity = 100;

    /** Tokens greedily refilled per minute. */
    private long refillPerMinute = 100;

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public long getRefillPerMinute() {
        return refillPerMinute;
    }

    public void setRefillPerMinute(long refillPerMinute) {
        this.refillPerMinute = refillPerMinute;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration() {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(capacity, refillPerMinute));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
