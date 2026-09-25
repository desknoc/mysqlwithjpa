package com.sena.mysqlwithjpa.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Input sanitizer for free-text API fields. A blocklist of exploit payload
 * shapes (HTML/script markup, event-handler attributes, path traversal) is
 * REJECTED outright; everything else — including legitimate Unicode names —
 * passes through byte-identical. Violations throw {@link SanitizationException}.
 */
public final class Sanitizer {

    private static final List<Pattern> REJECTED_PATTERNS = List.of(
            // Any HTML-tag-shaped construct: <script>, <img ...>, </b>, ...
            Pattern.compile("<\\s*/?\\s*[a-zA-Z][^>]*>", Pattern.CASE_INSENSITIVE),
            // Inline event-handler attributes: onerror=..., onclick = ...
            Pattern.compile("\\bon[a-zA-Z]+\\s*=", Pattern.CASE_INSENSITIVE),
            // Shell path traversal segments
            Pattern.compile("\\.\\.[/\\\\]"));

    private Sanitizer() {
        // Utility class — no instances.
    }

    /**
     * Returns {@code value} unchanged when clean; throws
     * {@link SanitizationException} naming {@code field} when rejected.
     * {@code null} and empty values are clean (optional fields stay optional).
     */
    public static String requireClean(String field, String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        for (Pattern pattern : REJECTED_PATTERNS) {
            if (pattern.matcher(value).find()) {
                throw new SanitizationException(field, "rejected content");
            }
        }
        return value;
    }
}
