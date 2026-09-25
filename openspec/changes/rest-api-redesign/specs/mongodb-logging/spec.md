# mongodb-logging Specification

## Purpose

Defines system-log persistence into MongoDB via `spring-boot-starter-data-mongodb`. Logs are written into separate collections per severity level — `info`, `warns`, and `error` — with lazy collection creation (collections are created by MongoDB on the first insert; no migration or provisioning tooling is required).

## Requirements

### Requirement: Per-level collection routing

The logging component MUST persist log entries into the MongoDB collection matching their severity: informational entries into `info`, warnings into `warns`, and errors into `error`. An entry MUST NOT be duplicated into a lower-severity collection (an error MUST NOT also appear in `warns` or `info`).

#### Scenario: Info entry lands in the info collection

- GIVEN the MongoDB logger is configured
- WHEN the application logs an informational event
- THEN a document for that event exists in the `info` collection
- AND no document for that event exists in `warns` or `error`

#### Scenario: Warning entry lands in the warns collection

- GIVEN the MongoDB logger is configured
- WHEN the application logs a warning event
- THEN a document for that event exists in the `warns` collection
- AND no document for that event exists in `info` or `error`

#### Scenario: Error entry lands in the error collection

- GIVEN the MongoDB logger is configured
- WHEN the application logs an error event
- THEN a document for that event exists in the `error` collection

### Requirement: Log document content

Each persisted log entry SHOULD contain, at minimum: a timestamp, the severity level, the message, and the logger/component name. Stack traces SHOULD be captured for error events when present. Log documents MUST NOT contain secrets (passwords, plaintext credentials, or connection strings).

#### Scenario: Entry includes required fields

- GIVEN a log event with a message and component
- WHEN the event is persisted to MongoDB
- THEN the stored document includes timestamp, level, message, and component fields

#### Scenario: No secrets in logs

- GIVEN a user-creation request processed end-to-end
- WHEN any resulting log entries are recorded in MongoDB
- THEN no log document contains the submitted plaintext password, the BCrypt hash, or database credentials

### Requirement: Lazy collection creation

Collections MUST be created lazily by MongoDB upon the first insert for that level. The application MUST NOT require any pre-created collections, provisioning scripts, or migration tooling to start or operate.

#### Scenario: Fresh database requires no provisioning

- GIVEN a MongoDB database with no existing `info`/`warns`/`error` collections
- WHEN the application starts and logs its first event of each level
- THEN the corresponding collections come into existence via first insert
- AND application startup did not depend on pre-provisioned collections

### Requirement: Graceful degradation when MongoDB is unavailable

MongoDB logging failures MUST NOT break the API's primary behavior. When MongoDB is unreachable, the application MUST continue to serve requests; logging write failures MAY be reported to the console/local log. Whether MongoDB logging is made optional via a property is a design-phase decision; the non-blocking behavior is a hard requirement.

#### Scenario: MongoDB outage does not fail API requests

- GIVEN the MongoDB instance is unreachable
- WHEN the API processes a normal CRUD request that would normally surface log events
- THEN the API responds with its normal business status (2xx/4xx per the operation)
- AND the request does not fail with a 500 caused by a logging error

#### Scenario: Application startup survives missing MongoDB (if logging is optional)

- GIVEN logging to MongoDB is disabled or unreachable by configuration
- WHEN the application starts
- THEN the application either starts successfully or fails by explicit config validation — NOT by an incidental connection failure at request time
