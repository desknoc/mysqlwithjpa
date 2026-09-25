# user-rest-crud Specification

## Purpose

Defines the REST contract for user-management CRUD exposed by the user-management microservice of the SENA financial-management application. The API is consumed by the React frontend at `http://localhost:3000`; authentication/login is out of scope (owned by the existing Express backend). This spec covers the layered `@RestController` → Service → Repository flow and global error handling. The concrete base path (`/api/users` vs `/users`) is a design-phase decision; scenarios below describe endpoint behavior abstractly as "the users resource".

## Requirements

### Requirement: REST user creation endpoint

The API MUST expose an endpoint that creates a user from a JSON request body. The request body MUST contain `primerNombre`, `primerApellido`, `tipoDocumento`, `documento`, `celular`, `correoElectronico`, and `contrasena` (plaintext); it MAY contain `segundoNombre`, `segundoApellido`, `grupoFormacion`, `rol`, and `tipoApoyo`. On success the API MUST return HTTP 201 with the persisted user representation. The response MUST NOT include the password field in any form (plaintext or hash). The API MUST NOT expose any login/authentication endpoint.

#### Scenario: Successful creation with required fields only

- GIVEN the API is configured and reachable
- WHEN a client sends a request to create a user with all required fields valid, no `rol`, and no optional fields
- THEN the API responds with HTTP 201
- AND the response body contains the created user including its server-assigned identifier
- AND the response body does not contain `contrasena` or its hash

#### Scenario: Successful creation with optional fields and explicit role

- GIVEN a valid creation request that includes `segundoNombre`, `segundoApellido`, `grupoFormacion`, `rol` = `ADMIN`, and `tipoApoyo` = `alimentacion`
- WHEN the request is processed
- THEN the API responds with HTTP 201
- AND the response body reflects the supplied optional field values

#### Scenario: Creation rejected for missing required field

- GIVEN a creation request missing `primerNombre`
- WHEN the request is processed
- THEN the API responds with HTTP 400 via the global error envelope
- AND no user is persisted

### Requirement: Read user by identifier

The API MUST expose an endpoint that returns a single user by its identifier. The response MUST NOT include the password field or hash.

#### Scenario: Existing user found

- GIVEN a user exists with a known identifier
- WHEN a client requests that user by id
- THEN the API responds with HTTP 200
- AND the response body contains the user's fields
- AND the response body does not contain the password or its hash

#### Scenario: User not found

- GIVEN no user exists for the requested identifier
- WHEN a client requests that user
- THEN the API responds with HTTP 404 via the global error envelope

### Requirement: Replaceable user update endpoint

The API MUST expose an endpoint that updates an existing user's updatable fields. Trigger-managed fields (`fechaRegistro`, `ultimaActualizacion`) MUST NOT be writable by clients; if supplied, they MUST be ignored or rejected. When the update includes a new `contrasena`, validation and hashing rules from `api-security` MUST apply; when omitted, the stored password MUST remain unchanged.

#### Scenario: Successful update

- GIVEN an existing user
- WHEN a client sends a valid update changing `celular` and `correoElectronico`
- THEN the API responds with HTTP 200 and the updated representation
- AND `ultimaActualizacion` in the persisted record has changed (set by the database, not by the client)

#### Scenario: Update of non-existent user

- GIVEN no user exists for the identifier in the update request
- WHEN the update request is processed
- THEN the API responds with HTTP 404 via the global error envelope

#### Scenario: Update without password leaves existing hash untouched

- GIVEN an existing user with a known stored hash
- WHEN a valid update is submitted without a `contrasena` field
- THEN the API responds with HTTP 200
- AND the stored password hash is byte-identical to its previous value

### Requirement: User deletion endpoint

The API MUST expose an endpoint that deletes a user by identifier.

#### Scenario: Existing user deleted

- GIVEN an existing user
- WHEN a client requests deletion of that user
- THEN the API responds with HTTP 204 (or 200 with a confirmation body, per the design decision)
- AND subsequent reads of that identifier return 404

#### Scenario: Deletion of non-existent user

- GIVEN no user exists for the requested identifier
- WHEN a deletion request is processed
- THEN the API responds with HTTP 404 via the global error envelope

### Requirement: Uniqueness conflict handling

The API MUST map uniqueness violations on `documento` and `correoElectronico` to HTTP 409 via the global error envelope. Pre-insert uniqueness checks in the service layer MUST be the primary mechanism; database constraint violations MUST also be caught and mapped to 409 as a fallback.

#### Scenario: Duplicate document rejected

- GIVEN a user exists with `documento` = 1234567890
- WHEN a creation request arrives with `documento` = 1234567890
- THEN the API responds with HTTP 409 via the global error envelope
- AND the error message identifies the conflicting field

#### Scenario: Duplicate email rejected

- GIVEN a user exists with `correoElectronico` = "ana@example.com"
- WHEN a creation request arrives with the same email
- THEN the API responds with HTTP 409 via the global error envelope

### Requirement: Global error envelope

All application errors MUST be returned through the existing `ApiError`-style envelope (timestamp, status, error, message, path). The mapping MUST cover at minimum: 400 (validation errors, missing/invalid fields), 404 (missing resource), 409 (uniqueness conflict), 429 (rate limit exceeded — see `api-security`), and 500 (unhandled). The 500 mapping MUST NOT leak internal details (stack traces, SQL, database messages) to the client.

#### Scenario: Internal error does not leak internals

- GIVEN the persistence layer throws an unexpected error
- WHEN the failure propagates to the controller
- THEN the API responds with HTTP 500 in the standard envelope
- AND the message contains no stack trace, SQL fragment, or vendor error text

### Requirement: Static frontend and legacy demo routes removed

The `src/main/resources/static/**` assets MUST be deleted and the `/demo/**` endpoints MUST no longer exist. Requests to the legacy routes MUST return 404.

#### Scenario: Legacy demo route gone

- GIVEN the redesigned application is running
- WHEN a client sends POST `/demo/add` or GET `/demo/all`
- THEN the API responds with HTTP 404
