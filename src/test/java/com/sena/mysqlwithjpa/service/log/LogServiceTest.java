package com.sena.mysqlwithjpa.service.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito slice for {@link LogService}: per-level collection routing
 * (info/warns/error, never duplicated into a lower-severity collection),
 * document content (timestamp, level, message, component, stackTrace on
 * errors), secret hygiene, opt-out flag, and graceful degradation when the
 * Mongo write fails. No Spring context, no Docker, no live MongoDB.
 */
@ExtendWith(MockitoExtension.class)
class LogServiceTest {

    private static final String PLAINTEXT = "sup3r-segura";
    private static final String BCRYPT_PREFIX = "$2b$10$";
    private static final String MONGO_URI_PREFIX = "mongodb://";

    @Mock
    private MongoTemplate mongoTemplate;

    private LogService enabledService() {
        return new LogService(mongoTemplate, true);
    }

    private static ArgumentCaptor<LogEntry> captureSingleWrite(String collection) {
        return ArgumentCaptor.forClass(LogEntry.class);
    }

    @Test
    void logInfoWritesOneDocumentToInfoOnly() {
        LogService logService = enabledService();

        logService.logInfo("UserService", "user created id=7");

        ArgumentCaptor<LogEntry> captor = captureSingleWrite("info");
        verify(mongoTemplate).save(captor.capture(), eq("info"));
        verify(mongoTemplate, never()).save(any(), eq("warns"));
        verify(mongoTemplate, never()).save(any(), eq("error"));

        LogEntry entry = captor.getValue();
        assertNotNull(entry.timestamp(), "stored document must include a timestamp");
        assertEquals("INFO", entry.level());
        assertEquals("user created id=7", entry.message());
        assertEquals("UserService", entry.component());
        assertNull(entry.stackTrace(), "info entries carry no stack trace");
    }

    // Triangulation: different level routes to a different code path and collection.
    @Test
    void logWarnWritesOneDocumentToWarnsOnly() {
        LogService logService = enabledService();

        logService.logWarn("UserService", "rate limit nearly exhausted");

        ArgumentCaptor<LogEntry> captor = captureSingleWrite("warns");
        verify(mongoTemplate).save(captor.capture(), eq("warns"));
        verify(mongoTemplate, never()).save(any(), eq("info"));
        verify(mongoTemplate, never()).save(any(), eq("error"));

        LogEntry entry = captor.getValue();
        assertNotNull(entry.timestamp());
        assertEquals("WARN", entry.level());
        assertEquals("rate limit nearly exhausted", entry.message());
        assertEquals("UserService", entry.component());
    }

    @Test
    void logErrorWritesToErrorCollectionIncludingTheStackTrace() {
        LogService logService = enabledService();
        IllegalStateException failure = new IllegalStateException("boom");

        logService.logError("UserService", "user delete failed id=7", failure);

        ArgumentCaptor<LogEntry> captor = captureSingleWrite("error");
        verify(mongoTemplate).save(captor.capture(), eq("error"));
        verify(mongoTemplate, never()).save(any(), eq("info"));
        verify(mongoTemplate, never()).save(any(), eq("warns"));

        LogEntry entry = captor.getValue();
        assertEquals("ERROR", entry.level());
        assertEquals("user delete failed id=7", entry.message());
        assertEquals("UserService", entry.component());
        assertNotNull(entry.stackTrace(), "error entries must capture the stack trace");
        assertTrue(entry.stackTrace().contains("java.lang.IllegalStateException: boom"),
                "stack trace must include the exception type and message");
    }

    // Spec: no log document contains plaintext passwords, BCrypt hashes, or
    // connection strings. The document shape carries only component + action
    // context; this test pins that guarantee using representative secret values.
    @Test
    void loggedEntriesNeverContainSecrets() {
        LogService logService = enabledService();
        String operationMessage = "user created id=7";

        logService.logInfo("UserService", operationMessage);
        logService.logWarn("UserService", "duplicate documento rejected id-pending");
        logService.logError("UserService", "user delete failed id=7",
                new IllegalStateException("boom"));

        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        verify(mongoTemplate, org.mockito.Mockito.times(3)).save(captor.capture(), any());
        for (LogEntry entry : captor.getAllValues()) {
            String serialized = String.valueOf(entry.component()) + " | "
                    + entry.message() + " | " + String.valueOf(entry.stackTrace());
            assertFalse(serialized.contains(PLAINTEXT), "plaintext password must never be logged");
            assertFalse(serialized.contains(BCRYPT_PREFIX), "BCrypt hashes must never be logged");
            assertFalse(serialized.contains(MONGO_URI_PREFIX),
                    "connection strings must never be logged");
            assertFalse(serialized.toLowerCase().contains("contrasena"),
                    "credential field names must never be logged");
        }
    }

    // 6.3 RED: Mongo write failure must not propagate to the caller.
    @Test
    void mongoWriteFailureIsSwallowedAndNeverPropagates() {
        LogService logService = enabledService();
        doThrow(new IllegalStateException("mongo unreachable"))
                .when(mongoTemplate).save(any(), eq("info"));

        Logger logServiceLogger = (Logger) LoggerFactory.getLogger(LogService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logServiceLogger.addAppender(appender);
        try {
            assertDoesNotThrow(() -> logService.logInfo("UserService", "user created id=7"),
                    "a failing Mongo write must not break the business operation");
        } finally {
            logServiceLogger.detachAppender(appender);
        }

        assertFalse(appender.list.isEmpty(), "the failure must be reported to the SLF4J console log");
        assertTrue(appender.list.stream()
                        .anyMatch(e -> e.getFormattedMessage().contains("user created id=7")),
                "the SLF4J entry must identify the dropped log event");
    }

    // Triangulation: the degradation applies to every level, not just info.
    @Test
    void mongoWriteFailureIsSwallowedForErrorLoggingToo() {
        LogService logService = enabledService();
        doThrow(new IllegalStateException("mongo unreachable"))
                .when(mongoTemplate).save(any(), eq("error"));

        assertDoesNotThrow(() -> logService.logError("UserService", "op failed", new RuntimeException("x")));
    }

    // 6.3 RED: with app.logging.mongo.enabled=false every method is a no-op.
    @Test
    void disabledFlagMakesEveryMethodANoOp() {
        LogService logService = new LogService(mongoTemplate, false);

        logService.logInfo("UserService", "user created id=7");
        logService.logWarn("UserService", "warn");
        logService.logError("UserService", "op failed", new RuntimeException("x"));

        verifyNoInteractions(mongoTemplate);
    }
}
