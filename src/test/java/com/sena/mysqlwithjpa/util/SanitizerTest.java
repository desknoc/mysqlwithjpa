package com.sena.mysqlwithjpa.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract for {@link Sanitizer}: string fields accepted by the API must be
 * rejected when they carry common exploit payloads (script tags,
 * event-handler attributes, path traversal); legitimate Unicode names pass
 * through unchanged. Violations raise {@link SanitizationException}, which
 * Phase 4 maps to HTTP 400.
 */
class SanitizerTest {

    @ParameterizedTest(name = "rejects exploit payload: {0}")
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<SCRIPT>alert(1)</SCRIPT>",
            "<img src=x onerror=alert(1)>",
            "onerror=alert(1)",
            "ONCLICK = steal()",
            "../../../etc/passwd",
            "..\\..\\windows\\system32",
            "<b>not a name</b>"
    })
    void rejectsCommonExploitPayloads(String payload) {
        assertThatThrownBy(() -> Sanitizer.requireClean("primerNombre", payload))
                .isInstanceOf(SanitizationException.class)
                .hasMessageContaining("primerNombre");
    }

    @Test
    void acceptsUnicodeNamesPreservingCharactersExactly() {
        assertThat(Sanitizer.requireClean("primerNombre", "José Lía"))
                .isEqualTo("José Lía");
        assertThat(Sanitizer.requireClean("primerApellido", "María Fernanda Gómez"))
                .isEqualTo("María Fernanda Gómez");
    }

    @Test
    void acceptsNullAndBlankOptionalFields() {
        assertThat(Sanitizer.requireClean("segundoNombre", null)).isNull();
        assertThat(Sanitizer.requireClean("segundoApellido", "")).isEmpty();
    }

    @Test
    void acceptsOrdinaryPunctuationInFreeText() {
        assertThat(Sanitizer.requireClean("grupoFormacion", "ADSO-2026, noche (sede 2)"))
                .isEqualTo("ADSO-2026, noche (sede 2)");
    }
}
