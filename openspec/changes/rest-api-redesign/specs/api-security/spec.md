# api-security Specification

## Purpose

Defines the security behavior of the user-management API: BCrypt password hashing compatible with the Express backend's existing `$2b$10$` hashes, plaintext validation before hashing, input sanitization, CORS restricted to `http://localhost:3000`, and global per-IP rate limiting with HTTP 429. Authentication (login) is out of scope — it stays in the Express backend.

## Requirements

### Requirement: BCrypt hashing compatible with Express $2b$ hashes

The service layer MUST hash passwords with Spring Security's BCrypt encoder before persistence, and MUST be able to VERIFY existing `$2b$10$` hashes produced by the Node/Express backend. Compatibility is a hard requirement: a Spring-generated hash MUST verify against the Express-compatible checker and a known Express-generated `$2b$10$` hash MUST verify in Spring.

#### Scenario: Round-trip — Spring verifies a real $2b$10$ hash from the database dump

- GIVEN a known plaintext and a real `$2b$10$…` BCrypt hash taken from the existing production data dump
- WHEN the service verifies the plaintext with its BCrypt encoder
- THEN verification returns true
- AND verification of a wrong plaintext returns false

#### Scenario: Round-trip — Spring-generated hash verifies within Spring

- GIVEN any valid plaintext password
- WHEN the service hashes it with the configured BCrypt encoder
- THEN the produced hash is verifiable as matching the plaintext by the same encoder
- AND a different plaintext does not match

#### Scenario: Hash format and cost are acceptable to the shared database

- GIVEN a Spring-generated BCrypt hash
- WHEN the hash is inspected
- THEN it uses a supported BCrypt version marker (`$2a$` or `$2b$`)
- AND it is verifiable by standard BCrypt implementations used by the Express backend (cost 10 family)

### Requirement: Plaintext password validation before hashing

The service MUST validate plaintext password rules BEFORE hashing and BEFORE persistence. The plaintext MUST be at least 8 characters. Passwords failing validation MUST be rejected with HTTP 400; a rejected password MUST NOT reach the hash step or the database.

#### Scenario: Short password rejected

- GIVEN a creation request with `contrasena` = "abc123"
- WHEN the request is processed
- THEN the API responds with HTTP 400 via the global error envelope
- AND no user is persisted

#### Scenario: Valid-length password reaches hashing

- GIVEN a creation request with `contrasena` of 8 or more characters
- WHEN the request is processed
- THEN the password is hashed and persisted
- AND the stored value is not equal to the submitted plaintext

### Requirement: Input sanitization against common exploit payloads

String fields accepted by the API MUST be sanitized so common exploit payloads (e.g. script tags, event-handler attributes in names, shell path traversal characters in free-text fields) are rejected or neutralized before any downstream use. Sanitization failures MUST produce HTTP 400.

#### Scenario: XSS-shaped value in primerNombre rejected

- GIVEN a creation request with `primerNombre` = `<script>alert(1)</script>`
- WHEN the request is processed
- THEN the API responds with HTTP 400
- AND no user is persisted

#### Scenario: Legitimate names with Unicode letters pass

- GIVEN a creation request with `primerNombre` = "José Lía"
- WHEN the request is processed
- THEN the request is accepted (subject to other validation)
- AND the stored value preserves the characters correctly

### Requirement: Global per-IP rate limiting with bucket4j

The API MUST enforce a global per-IP rate limit implemented with bucket4j, applied across the API surface (not per-endpoint). When a single IP exceeds the configured threshold within its refill window, the API MUST respond with HTTP 429. The client identity SHOULD be derived from `X-Forwarded-For` when present, falling back to the remote address. Threshold and refill values are configuration with design-phase defaults; the maximum page size rules do not apply here.

#### Scenario: Requests within the limit succeed

- GIVEN a client IP that has made fewer requests than the configured limit in the current window
- WHEN a request arrives from that IP
- THEN the request is processed normally (status depends on the endpoint, never 429 for this request)

#### Scenario: Exceeding the limit returns HTTP 429

- GIVEN a client IP whose request count has reached the configured limit
- WHEN the next request arrives from that IP within the same window
- THEN the API responds with HTTP 429 via the global error envelope

#### Scenario: Limit is global, not per-endpoint

- GIVEN a client IP distributes requests across different user endpoints
- WHEN the summed requests exceed the limit within the window
- THEN subsequent requests to ANY endpoint from that IP return HTTP 429

#### Scenario: X-Forwarded-For identifies NATed clients independently

- GIVEN two clients behind the same remote address with different `X-Forwarded-For` values
- WHEN both clients make requests
- THEN each client is rate-limited by its own bucket, not the shared remote address

### Requirement: CORS restricted to http://localhost:3000

The API MUST apply a CORS policy whose only allowed origin is `http://localhost:3000`. Requests whose `Origin` is any other value MUST NOT receive CORS allowances (no `Access-Control-Allow-Origin` grant). Wildcards (`*`) MUST NOT be used.

#### Scenario: Request from the allowed origin is granted CORS

- GIVEN a request with header `Origin: http://localhost:3000`
- WHEN the request is processed (preflight and/or actual request)
- THEN the response includes CORS headers allowing that origin

#### Scenario: Request from a foreign origin is not granted CORS

- GIVEN a request with header `Origin: http://evil.example.com`
- WHEN the request is processed
- THEN the response does NOT contain `Access-Control-Allow-Origin: http://evil.example.com`
- AND it does NOT contain a wildcard allow-origin

#### Scenario: Non-browser requests without Origin are unaffected

- GIVEN a server-to-server request without an `Origin` header (e.g. curl, test client)
- WHEN the request is processed
- THEN it is handled normally by authentication-neutral logic (no CORS rejection of clients that do not use CORS)

### Requirement: No authentication endpoints

The API MUST NOT expose login, logout, token, or session endpoints. Any attempt against conventional auth paths (e.g. `/login`) MUST return HTTP 404.

#### Scenario: Login path does not exist

- GIVEN the redesigned application is running
- WHEN a client POSTs credentials to `/login`
- THEN the API responds with HTTP 404
