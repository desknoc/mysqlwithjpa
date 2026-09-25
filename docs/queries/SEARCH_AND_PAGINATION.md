# Search & Pagination

How the read-side querying works: two derived-query searches plus a paginated listing with a hard maximum of 7 records per page. Every query is a Spring Data derived method — user input always arrives as a bound parameter.

> **Verification note**: the controller/service slices are verified green (MockMvc + Mockito). The repository integration tests that exercise these derived queries against a real MySQL container are pending (Docker-gated; tasks 5.1/5.2 in `openspec/changes/rest-api-redesign/tasks.md` remain unchecked until `./mvnw test -Dtest=UserSearchRepositoryTest` runs with a Docker daemon).

## Endpoints

| Endpoint | Behavior |
|----------|----------|
| `GET /api/users?page=&size=` | Paginated listing of all users |
| `GET /api/users/search/and?primerNombre=&documento=&page=&size=` | Match only when **both** `primerNombre` AND `documento` equal the supplied values |
| `GET /api/users/search/or?term=&page=&size=` | Match when the term equals `primerNombre` OR `primerApellido` OR `documento` |

All three accept `page` (default 0) and `size` (default 7), always return HTTP 200 with a possibly-empty result (an empty search is not a 404), and serialize as `PagedModel<UserResponse>`. Responses never contain passwords.

## The derived queries

Declared on `UserRepository` — Spring Data turns the method names into parameterized JPQL at startup:

```java
Page<User> findByPrimerNombreAndDocumento(String primerNombre, Long documento, Pageable pageable);
Page<User> findByPrimerNombreOrPrimerApellidoOrDocumento(String primerNombre, String primerApellido, Long documento, Pageable pageable);
```

Because these are *derived* queries, there is no JPQL/SQL string to inject into. The property names map directly onto entity fields (`primerNombre`, `primerApellido`, `documento`).

### The OR term and type safety

`documento` is a `Long` (BIGINT). The controller parses the single OR `term`:

- If the term parses as a number, it is passed as the `documento` argument and also tried against the name fields.
- Otherwise the `documento` argument is `null`; a `null` OR branch simply never matches — no cast failure, no SQL error, and the parameterization is preserved.

## Hard maximum of 7 records

The effective page size can **never exceed 7**, on any listing or search endpoint. Oversized requests are silently clamped (not rejected with 400):

```java
PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)); // MAX_PAGE_SIZE = 7
```

Observed contract examples:

| Request | Result |
|---------|--------|
| `GET /api/users?page=0` with 10+ users | exactly 7 records |
| `GET /api/users?page=0&size=50` | clamped: 7 records, HTTP 200 |
| `GET /api/users?page=1` with 10 users | 3 records, `totalElements=10`, `totalPages=2` |
| `GET /api/users?page=99` with 10 users | 200, empty `content`, `totalElements=10` |
| OR search with 12 matches, page 0 | at most 7 records, `totalElements=12` |

## Pagination metadata

Responses use Spring Data's `PagedModel`, giving a stable JSON shape:

```json
{
  "content": [ { "id": 1, "primerNombre": "Ana", "...": "..." } ],
  "page": {
    "size": 7,
    "number": 0,
    "totalElements": 10,
    "totalPages": 2
  }
}
```

`totalElements` and `totalPages` always reflect the full result set, even on empty or out-of-range pages, so the React UI can render paginators without special cases.

## Injection safety: parameterized by construction

Because every search is a derived query, the quoted examples in user input become **data, not SQL**. Example threat case from the spec:

```
GET /api/users/search/or?term=Ana' OR '1'='1
```

- The term `"Ana' OR '1'='1"` is bound as a literal string parameter to the `primerNombre`/`primerApellido` comparisons (the `documento` branch gets `null` because the term is not numeric).
- No user record matches that literal value → HTTP 200 with an empty result.
- No SQL error is raised; the metacharacters never reach the query text.

There is no string-concatenated query anywhere in the codebase: the listing uses repository paging, and both searches are derived methods.
