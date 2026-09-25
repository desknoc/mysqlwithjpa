# user-search-pagination Specification

## Purpose

Defines the read-side querying behavior of the user-management API: two Spring Data derived-query searches and a paginated listing with a hard maximum page size of 7 records. All queries MUST be parameterized by construction (derived JPA methods), never string-concatenated.

## Requirements

### Requirement: AND search on primer_nombre and documento

The API MUST expose a search that matches users only when BOTH `primerNombre` equals the supplied value AND `documento` equals the supplied value. It MUST be implemented as a Spring Data derived query method (parameterized by construction), not by string-built JPQL/SQL.

#### Scenario: Both criteria match — user returned

- GIVEN a user exists with `primerNombre` = "Ana" and `documento` = 1234567890
- WHEN the AND search is invoked with `primerNombre` = "Ana" and `documento` = 1234567890
- THEN the response contains that user
- AND it contains no user failing either criterion

#### Scenario: Only one criterion matches — user not returned

- GIVEN a user exists with `primerNombre` = "Ana" and `documento` = 1234567890
- WHEN the AND search is invoked with `primerNombre` = "Ana" and `documento` = 9876543210
- THEN the response does not contain that user
- AND the API responds with HTTP 200 (empty result), not an error

#### Scenario: Empty result is a valid 200, not 404

- GIVEN no user matches both criteria
- WHEN the AND search is invoked
- THEN the API responds with HTTP 200 and an empty result set

### Requirement: OR search on primer_nombre, primer_apellido, documento

The API MUST expose a search that matches users when the supplied value equals `primerNombre` OR `primerApellido` OR `documento`. It MUST be implemented as a Spring Data derived query method.

#### Scenario: Value matching only primer_apellido still returns the user

- GIVEN a user exists with `primerApellido` = "Gomez" and `primerNombre` = "Ana"
- WHEN the OR search is invoked with the term "Gomez"
- THEN the response contains that user

#### Scenario: Single term matching multiple fields returns all matches

- GIVEN one user with `primerNombre` = "Luis" and a different user with `documento` = 1122334455
- WHEN the OR search term is a value equal to both `primerNombre` of the first user and non-matching for the second (e.g. "Luis")
- THEN only the matching user(s) are returned

#### Scenario: Numeric documento term in OR search type safety

- GIVEN the query term is compatible with the `documento` BIGINT column
- WHEN the OR search is executed
- THEN the query is parameterized with a correctly typed value and no SQL error or cast failure occurs

### Requirement: Paginated user listing with hard max page size 7

The API MUST expose a paginated listing via `JpaRepository` + `Pageable`. The effective page size MUST NEVER exceed 7 records, even when the client requests more; requests above 7 MUST be clamped to 7 (design decides clamp vs 400; this spec mandates the maximum effective size is 7). Responses MUST include standard pagination metadata: current page, page size, total elements, and total pages.

#### Scenario: Full page of 7 records

- GIVEN more than 7 users exist
- WHEN a client requests page 0 with default size
- THEN the response contains exactly 7 records
- AND the metadata reports the correct total elements and total pages

#### Scenario: Oversized page request clamped to 7

- GIVEN more than 7 users exist
- WHEN a client requests page size 50
- THEN the effective page size is 7
- AND the response contains at most 7 records

#### Scenario: Last partial page

- GIVEN 10 users exist
- WHEN a client requests page 1
- THEN the response contains exactly 3 records
- AND the metadata reports total elements = 10 and total pages = 2

#### Scenario: Out-of-range page returns empty content

- GIVEN 10 users exist
- WHEN a client requests page 99
- THEN the API responds with HTTP 200
- AND the content list is empty
- AND the metadata still reports total elements = 10

### Requirement: Parameterized queries only

All search and listing queries MUST be constructed so that user input arrives as bound parameters. No query string fragment from the client MUST EVER be interpolated into SQL or JPQL text.

#### Scenario: Injection-shaped input is treated as data

- GIVEN an input term containing SQL metacharacters (e.g. `"Ana' OR '1'='1"`)
- WHEN the AND or OR search is invoked with that term
- THEN the search treats it as a literal value
- AND no user record is returned (no such literal value exists)
- AND no SQL error is raised

### Requirement: Searches and pagination interoperate

Search endpoints SHOULD accept pagination parameters and MUST apply the same maximum page size of 7 when paginated. Unpaginated search responses MUST NOT grow unbounded for the OR search: if unpaginated OR search results are exposed, the design MUST bound them; the paging contract above is the default surface.

#### Scenario: Paginated OR search respects the 7-record maximum

- GIVEN 12 users match an OR search term
- WHEN the OR search is invoked with page 0
- THEN the response contains at most 7 records
- AND the metadata reports total elements = 12
