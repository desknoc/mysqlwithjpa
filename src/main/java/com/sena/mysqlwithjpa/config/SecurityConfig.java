package com.sena.mysqlwithjpa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Crypto-only security setup (design Decision 8).
 *
 * Spring Security is on the classpath solely as the source of
 * {@link BCryptPasswordEncoder}. Because Boot auto-configures a restrictive
 * default chain as soon as the starter is present, this class declares an
 * explicit, fully open {@link SecurityFilterChain}: every request is
 * permitted, CSRF protection is disabled (stateless JSON API), and neither
 * form login nor HTTP Basic are enabled. Authentication stays in the sibling
 * Express backend. CORS is delegated to the dedicated {@link CorsConfig}
 * bean via {@code cors(withDefaults())}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * BCrypt encoder at strength 10. Spring's implementation emits
     * {@code $2a$10$} hashes and verifies the {@code $2a$}/{@code $2b$}/{@code $2y$}
     * family identically, so existing Express-produced {@code $2b$10$} hashes
     * remain valid.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
