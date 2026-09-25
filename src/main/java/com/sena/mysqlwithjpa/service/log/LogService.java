package com.sena.mysqlwithjpa.service.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

/**
 * System logger persisting entries into per-level MongoDB collections:
 * {@code info}, {@code warns}, and {@code error}. Collections are created
 * lazily by MongoDB on first insert — no provisioning required. All writes are
 * gated on {@code app.logging.mongo.enabled} (default {@code true}); when
 * {@code app.logging.mongo.enabled} (default {@code true}); when
 * disabled, every method is a no-op. A failing Mongo write is swallowed and
 * reported to the SLF4J console so logging outages never break the API's
 * primary behavior. {@link MongoTemplate} connects lazily on first write, so
 * application startup never blocks on Mongo reachability.
 */
@Service
public class LogService {

    private static final Logger log = LoggerFactory.getLogger(LogService.class);

    static final String COLLECTION_INFO = "info";
    static final String COLLECTION_WARNS = "warns";
    static final String COLLECTION_ERROR = "error";

    private final MongoTemplate mongoTemplate;
    private final boolean enabled;

    public LogService(MongoTemplate mongoTemplate,
                      @Value("${app.logging.mongo.enabled:true}") boolean enabled) {
        this.mongoTemplate = mongoTemplate;
        this.enabled = enabled;
    }

    public void logInfo(String component, String message) {
        write(COLLECTION_INFO, new LogEntry(Instant.now(), "INFO", message, component, null));
    }

    public void logWarn(String component, String message) {
        write(COLLECTION_WARNS, new LogEntry(Instant.now(), "WARN", message, component, null));
    }

    public void logError(String component, String message, Throwable throwable) {
        write(COLLECTION_ERROR,
                new LogEntry(Instant.now(), "ERROR", message, component, stackTraceOf(throwable)));
    }

    private void write(String collection, LogEntry entry) {
        if (!enabled) {
            return;
        }
        try {
            mongoTemplate.save(entry, collection);
        } catch (RuntimeException e) {
            // Graceful degradation (design Decision 6): a logging outage must
            // never fail the business operation; report and continue.
            log.warn("MongoDB log write to '{}' failed; dropping entry [{}] {}: {}",
                    collection, entry.component(), entry.message(), e.toString());
        }
    }

    static String stackTraceOf(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
