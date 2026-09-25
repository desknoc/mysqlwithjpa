package com.sena.mysqlwithjpa.service.log;

import java.time.Instant;

/**
 * One system-log document persisted into MongoDB. Carries only operational
 * context (when, severity, what happened, which component, and the stack trace
 * for errors); it never carries business payloads, so secrets cannot leak into
 * the log collections through this type.
 *
 * @param timestamp  when the event was recorded (UTC)
 * @param level      severity: {@code INFO}, {@code WARN}, or {@code ERROR}
 * @param message    the event description (component + action context only)
 * @param component  the originating component name (e.g. {@code "UserService"})
 * @param stackTrace the throwable's stack trace for error entries, otherwise null
 */
public record LogEntry(Instant timestamp, String level, String message,
                       String component, String stackTrace) {
}
