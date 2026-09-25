package com.sena.mysqlwithjpa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS policy for the whole API surface (design Decision 3, api-security spec):
 * the ONLY allowed origin is the React dev/prod frontend at
 * {@code http://localhost:3000}. No wildcards; foreign origins receive no
 * allow-origin grant at all. Consumed by the security filter chain via
 * {@code cors(withDefaults())} in {@link SecurityConfig}.
 */
@Configuration
public class CorsConfig {

    public static final String ALLOWED_ORIGIN = "http://localhost:3000";

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(ALLOWED_ORIGIN));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
