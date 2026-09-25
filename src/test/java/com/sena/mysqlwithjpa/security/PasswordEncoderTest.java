package com.sena.mysqlwithjpa.security;

import com.sena.mysqlwithjpa.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract for the BCrypt {@link PasswordEncoder} bean exposed by
 * {@link SecurityConfig}: it must verify existing Express-produced
 * {@code $2b$10$} hashes and produce fresh hashes in the BCrypt
 * {@code $2a$}/{@code $2b$} family at cost 10.
 *
 * This is a plain unit test: the bean method has no web dependencies, so the
 * contract the application context will publish is exercised directly.
 */
class PasswordEncoderTest {

    // Documented cross-stack test pair standing in for a production dump hash:
    // a $2b$10$ hash of the plaintext "password", generated with bcryptjs
    // (the bcrypt implementation Express backends use) for this test suite.
    // No real credentials are embedded in this test.
    private static final String KNOWN_PLAINTEXT = "password";
    private static final String KNOWN_EXPRESS_STYLE_HASH =
            "$2b$10$.7FBVSXoTgHy9jZ9J1L5feJJl4GigYu8jtuBMpWvH5DF7dJOKohOO";

    private final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

    @Test
    void verifiesAKnownExpressStyleHashFromTheSharedDatabase() {
        assertThat(encoder.matches(KNOWN_PLAINTEXT, KNOWN_EXPRESS_STYLE_HASH))
                .as("Spring BCrypt must verify a real $2b$10$ Express/bcrypt hash")
                .isTrue();
        assertThat(encoder.matches("definitely-wrong", KNOWN_EXPRESS_STYLE_HASH))
                .as("a wrong plaintext must not verify")
                .isFalse();
    }

    @Test
    void freshHashVerifiesItsOwnPlaintextOnly() {
        String hash = encoder.encode("unaClaveSegura123");

        assertThat(hash)
                .as("Spring must emit a BCrypt $2a$/$2b$ hash")
                .matches("^\\$2[ab]\\$.*");
        assertThat(encoder.matches("unaClaveSegura123", hash)).isTrue();
        assertThat(encoder.matches("otraClave456", hash)).isFalse();
        assertThat(hash)
                .as("hashing must never leak the plaintext back into storage")
                .doesNotContain("unaClaveSegura123");
    }

    @Test
    void freshHashesUseTheConfiguredCostOfTen() {
        String hash = encoder.encode("costCheckPass");

        assertThat(hash)
                .as("cost factor 10 keeps parity with the Express-produced hashes")
                .startsWith("$2a$10$");
    }
}
