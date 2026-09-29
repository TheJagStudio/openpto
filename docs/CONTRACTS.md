# OpenPTO — architecture & API contracts (source of truth)

OpenPTO is a free, open SaaS-style portal that modernizes legacy patent/trademark data access
(USPTO ODP / TSDR equivalents). Anyone can sign up (no payments). Everything runs locally; each
component is shaped so it maps 1:1 onto AWS (see "AWS mapping").

All services: Java 21, Spring Boot 4.1.1, Gradle (Groovy DSL, own wrapper), package root `gov.openpto.*`.
Toolchain: `. D:\JavaSpring\tools\env.ps1` (JDK 21, Node 24, Postgres 18 bins). Build a service with
`.\gradlew.bat build` from its folder.

## Components & ports

| Component | Folder | Port | AWS equivalent |
|---|---|---|---|
| API gateway | `gateway/` | 8080 | Amazon API Gateway (API keys, usage plans, throttling) |
| Open Data service | `odp-service/` | 8081 | ECS/Fargate service + Aurora PostgreSQL |
| Fee calculation service | `fee-service/` | 8082 | ECS/Fargate (or Lambda) microservice |
| Ingest service (+ `transform-lambda` subproject) | `ingest-service/` | 8083 | S3 bucket + S3 event notification + Lambda |
| Web app (Angular 22 + ZardUI + Tailwind 4) | `web/` | 4200 | S3 + CloudFront |
| PostgreSQL 18 | `D:\JavaSpring\data\pg` | 5433 | Aurora PostgreSQL |

The browser talks **only** to the gateway (`/api/...`, dev proxy from 4200 → 8080). Services never
call each other except the explicitly listed internal calls.

## Database

- One Postgres DB `openpto` (localhost:5433, user `openpto`, password `openpto`); tests use DB `openpto_test`.
- **Each service owns its own schema** and never touches another schema: `odp`, `fees`, `ingest`.
  Flyway per service: `spring.flyway.schemas=<schema>`, `spring.flyway.default-schema=<schema>`,
  `spring.jpa.properties.hibernate.default_schema=<schema>`, `ddl-auto: validate`, `open-in-view: false`.
- Config via env with dev defaults: `DB_URL` (default `jdbc:postgresql://localhost:5433/openpto`), `DB_USER`, `DB_PASSWORD`.
- Tests: pure unit tests (JUnit 5 + Mockito + AssertJ) need no DB. DB-backed tests use profile `test`
  pointing at `jdbc:postgresql://localhost:5433/openpto_test` (override `TEST_DB_URL`) with the service's
  own schema; they must be safe to run concurrently with other services' tests (only touch your schema).

## Cross-cutting conventions

- JSON camelCase. `LocalDate` → `"2025-01-19"`, `Instant` → ISO-8601 UTC. Money → JSON number with 2 decimals (BigDecimal, scale 2, HALF_UP).
- Errors: RFC 7807 `application/problem+json`:
  `{ "type", "title", "status", "detail", "instance", "requestId", "errors": [{ "field", "message" }] }`
  (`errors` only for validation failures). No stack traces.
- Correlation: every service reads/propagates `X-Request-Id` (gateway generates if absent) into MDC and the response.
- Pagination response (all list endpoints), own record, NOT Spring's Page:
  `{ "content": [...], "page": 0, "size": 20, "totalElements": 123, "totalPages": 7 }`. `size` max 100.
  Query params: `page` (0-based), `size`, `sort=field,asc|desc`.
- Actuator: `health` (with liveness/readiness probes), `info`, `metrics`, `prometheus` if available. Graceful shutdown.
- OpenAPI: springdoc at `/v3/api-docs` and `/swagger-ui.html` in every service, with title/description/tags and
  examples on DTOs (`@Schema`). The gateway aggregates them.
- Shared secret for internal calls: header `X-Internal-Token`, env `INTERNAL_TOKEN`
  (dev default `dev-internal-token-change-me`). `/internal/**` endpoints require it; the gateway never routes `/internal/**`.
- Security headers on; CORS only on the gateway (`http://localhost:4200`).

## Identity (issued by odp-service)

- JWT RS256, issuer `openpto`, 8h expiry. Claims: `sub` (user UUID), `email`, `name`, `roles` (e.g. `["USER"]`, `["USER","ADMIN"]`).
- Key pair persisted at `${app.jwt.key-dir:../data/keys}` (generated on first start) so restarts keep tokens valid.
- JWKS: `GET http://localhost:8081/.well-known/jwks.json`. Gateway and ingest-service validate with
  `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` and map claim `roles` → `ROLE_<role>`.
- Dev seed admin: `admin@openpto.local`, password from env `ADMIN_PASSWORD` (dev default `Admin#12345`).

## odp-service (8081) — Open Data Portal + accounts

Public data (permitAll at the service; the gateway enforces keys and limits):

| Method & path | Description |
|---|---|
| `GET /api/v1/patents` | Search. Params: `q` (full-text over title/abstract/assignee/inventor), `type` (UTILITY, DESIGN, PLANT, REISSUE), `status` (PENDING, GRANTED, ABANDONED, EXPIRED), `cpc` (prefix, e.g. `G06F`), `assignee`, `inventor`, `filedFrom`, `filedTo`, `grantedFrom`, `grantedTo`, `page`, `size`, `sort` (`grantDate`, `filingDate`, `patentNumber`, `relevance`). Returns page of `PatentSummary`. |
| `GET /api/v1/patents/facets` | Same filters; returns `{ types:[{value,count}], statuses:[...], years:[...], cpcSections:[...], topAssignees:[...] }` |
| `GET /api/v1/patents/{patentNumber}` | `PatentDetail`; 404 ProblemDetail if unknown |
| `GET /api/v1/trademarks` | Search. `q` (mark text/owner/goods), `status` (LIVE_PENDING, LIVE_REGISTERED, DEAD_ABANDONED, DEAD_CANCELLED), `niceClass` (1–45), `owner`, `filedFrom`, `filedTo`, paging, `sort` (`filingDate`, `registrationDate`, `serialNumber`, `relevance`). |
| `GET /api/v1/trademarks/{serialNumber}` | `TrademarkDetail` (TSDR-style: status, owner, goods & services by class, prosecution history) |
| `GET /api/v1/stats` | `{ patents, trademarks, patentsByYear:[{year,count}], trademarksByStatus:[{value,count}], lastIngestAt }` |
| `GET /api/v1/datasets/patents.{json\|csv}` | Streamed bulk export honoring the search filters, max 10,000 rows |
| `GET /api/v1/datasets/trademarks.{json\|csv}` | Same for trademarks |

`PatentSummary`: `{ patentNumber:"US11234567B2", applicationNumber:"17/123,456", title, type, status, filingDate, grantDate, primaryCpc, assignees:[string], inventors:[string], abstractSnippet, source:"SEED"|"INGEST" }`

`PatentDetail`: summary fields + `{ abstract, claims:[{number, text, independent, dependsOn}], cpcCodes:[string], citations:[{patentNumber, citedBy:"EXAMINER"|"APPLICANT"}], inventors:[{name, city, state, country}], assignees:[{name, city, state, country}], priorityDate, expirationDate, examiner, artUnit, ingestJobId, updatedAt }`

`TrademarkSummary`: `{ serialNumber:"97123456", registrationNumber, markText, markType:"STANDARD_CHARACTER"|"DESIGN"|"SOUND", status, filingDate, registrationDate, owner, niceClasses:[int] }`

`TrademarkDetail`: summary + `{ goodsAndServices:[{niceClass, description}], ownerAddress, attorney, filingBasis:"1A"|"1B"|"44D"|"44E"|"66A", events:[{date, code, description}], statusDate, source, ingestJobId }`

Accounts (JWT required unless noted):

| Method & path | Description |
|---|---|
| `POST /api/v1/auth/register` (public) | `{ email, password (min 10, 1 letter + 1 digit), displayName }` → 201 `AuthResponse`; 409 if email exists |
| `POST /api/v1/auth/login` (public) | `{ email, password }` → 200 `AuthResponse`; 401 on bad creds (same message for unknown email). Brute-force lockout: 5 failures → 15 min |
| `GET /api/v1/auth/me` | `UserResponse` |
| `GET /api/v1/account/api-keys` | `[ApiKeyResponse]` |
| `POST /api/v1/account/api-keys` | `{ name }` → 201 `ApiKeyResponse` + `key` (plaintext, shown once). Max 5 active keys per user (409) |
| `POST /api/v1/account/api-keys/{id}/rotate` | new plaintext `key`, old secret invalid |
| `DELETE /api/v1/account/api-keys/{id}` | revoke → 204 |
| `GET /api/v1/account/usage?days=30` | `{ totalRequests, daily:[{date, requests}], byKey:[{keyId, name, requests}] }` |
| `GET /api/v1/admin/users` (ADMIN) | page of users with key counts |

`AuthResponse`: `{ accessToken, tokenType:"Bearer", expiresIn (seconds), user: UserResponse }`
`UserResponse`: `{ id, email, displayName, roles:[...], createdAt }`
`ApiKeyResponse`: `{ id, name, prefix:"opto_ab12", tier:"FREE", createdAt, lastUsedAt, revokedAt, requestCount, key? }`
Key format: `opto_` + 40 random base62 chars; stored only as SHA-256 hash + first 9 chars as `prefix`.

Internal (require `X-Internal-Token`):

| Method & path | Description |
|---|---|
| `POST /internal/v1/api-keys/verify` | `{ key }` → `{ valid, keyId, userId, tier, perMinute, perDay }` (`valid:false` for unknown/revoked) |
| `POST /internal/v1/usage` | `{ entries:[{ keyId, date, count }] }` → 204 (upsert-add into daily usage) |
| `POST /internal/v1/patents/bulk-upsert` | `[PatentUpsert]` (≤ 1000) → `{ inserted, updated, failed:[{ id, error }] }`, keyed by `patentNumber` |
| `POST /internal/v1/trademarks/bulk-upsert` | `[TrademarkUpsert]` → same shape, keyed by `serialNumber` |

`PatentUpsert` = `PatentDetail` without `status`-derived/computed fields is fine; required: `patentNumber` or
`applicationNumber` (publications use `US20240123456A1` as `patentNumber`), `title`, `type`, `filingDate`; plus `ingestJobId`, `source:"INGEST"`.
`TrademarkUpsert` similar; required `serialNumber`, `markText`, `filingDate`.

Seed data (dev profile, `app.seed.enabled=true`, only when tables are empty): deterministic generator
(fixed Random seed) of ~5,000 realistic mock patents (varied CPC sections A–H, assignees, inventors,
claims 1–30 with dependencies, citations) and ~2,000 trademarks (classes 1–45, events). Batch inserts.

Search: Postgres full-text (`tsvector` generated column + GIN) with `websearch_to_tsquery('english', q)`
and `pg_trgm` for assignee/owner/mark similarity. Indexes on every filter column.

Tiers (returned by verify, enforced by gateway): `ANONYMOUS` (by client IP) 30/min & 1,000/day;
`FREE` (API key) 120/min & 20,000/day; `WEB` (valid user JWT, no key) 300/min & 50,000/day;
`ADMIN` 1,200/min & 1,000,000/day.

## fee-service (8082) — decoupled fee calculator

Owns schema `fees`: versioned fee schedules (`fee_schedule`, `fee_item` with large/small/micro amounts,
effective date ranges), saved quotes. All calculators pick the schedule effective on the given date
(default today). All endpoints public.

| Method & path | Description |
|---|---|
| `GET /api/v1/fees/schedules` | `[{ id, code:"FY2025", name, effectiveFrom, effectiveTo, current }]` |
| `GET /api/v1/fees/schedules/{code}` (or `current`) `?category=PATENT\|TRADEMARK` | `{ ...schedule, items:[{ feeCode, description, category, group, largeEntity, smallEntity, microEntity, unit:"EACH"\|"PER_CLAIM"\|"PER_CLASS"\|"PER_50_SHEETS"\|"PER_MONTH" }] }` |
| `POST /api/v1/fees/patent/filing` | `PatentFilingRequest` → `FeeQuote` |
| `POST /api/v1/fees/patent/maintenance` | `{ entitySize, grantDate, asOfDate? }` → `{ windows:[{ stage:"3.5"\|"7.5"\|"11.5", windowOpens, dueDate, graceEnds, status:"NOT_YET_OPEN"\|"OPEN"\|"GRACE_PERIOD"\|"EXPIRED"\|"PAID_WINDOW_PASSED", fee, surcharge, totalIfPaidOnAsOfDate }], patentExpired:boolean, scheduleCode }` |
| `POST /api/v1/fees/trademark` | `TrademarkFeeRequest` → `FeeQuote` |
| `POST /api/v1/fees/quotes` | `{ kind:"PATENT_FILING"\|"MAINTENANCE"\|"TRADEMARK", request, label? }` → 201 `{ id, ...FeeQuote, createdAt }` (server recomputes; never trusts client totals) |
| `GET /api/v1/fees/quotes/{id}` | saved quote |

`PatentFilingRequest`: `{ applicationType: UTILITY|DESIGN|PLANT|PROVISIONAL|REISSUE, entitySize: LARGE|SMALL|MICRO,
totalClaims (0–500), independentClaims (0–100, ≤ totalClaims), multipleDependentClaims: boolean, specificationSheets (0–10000),
filedElectronically: boolean (default true), lateFilingSurcharge: boolean, extensionMonths (0–5),
continuedExamination: NONE|FIRST|SUBSEQUENT, prioritizedExamination: boolean, filingDate?: date }`

`TrademarkFeeRequest`: `{ filingType: APPLICATION|STATEMENT_OF_USE|EXTENSION_SOU|SECTION_8|SECTION_15|SECTION_9_RENEWAL|SECTION_8_AND_9,
numberOfClasses (1–45), insufficientInformation: boolean, freeFormTextIds: boolean, extraCharacterBlocks (0–100),
inGracePeriod: boolean, filingDate?: date }`

`FeeQuote`: `{ scheduleCode, scheduleName, entitySize?, lineItems:[{ feeCode, description, quantity, unitAmount, amount }],
subtotals:[{ group, amount }], total, currency:"USD", notes:[string], warnings:[string] }`

Rules (patents): basic filing + search + examination per application type (provisional: filing only;
design/plant have their own codes); excess claims: each total claim over 20, each independent over 3
(reissue: independent over original count — treat as over 3), multiple-dependent once per application;
application size fee per additional 50 sheets or fraction over 100; non-electronic filing fee (not for
provisional/design? — utility only, and micro pays small rate); late filing surcharge; extension of time by months;
RCE first/subsequent; Track One prioritized examination (not for design/plant/provisional; max 4 independent/30 total claims → warning + 400).
Micro-entity rates are 20% and small 40% of large (schedule stores explicit amounts). Amounts are illustrative,
modeled on the USPTO schedule effective 2025-01-19 (code `FY2025`) and the prior schedule (code `FY2023`, 2022-12-29 → 2025-01-18).

The `legacy-fee-monolith` subproject contains the pre-extraction procedural code (doubles, magic numbers, one
giant method) and characterization tests prove the new service equals it for every valid input (documenting any
legacy rounding bugs that were intentionally fixed).

## ingest-service (8083) — legacy XML → JSON pipeline (S3 + Lambda locally)

Local emulation: `ObjectStorage` interface with `LocalFsObjectStorage` (buckets = folders under
`${app.storage.root:../data/buckets}`: `openpto-raw`, `openpto-processed`) and an `S3ObjectStorage` (AWS SDK v2,
profile `aws`, compile-only locally). A put into the raw bucket publishes an S3-shaped `ObjectCreated` event
(`{ bucket, key, size, eTag }`); an async listener invokes the **transform-lambda** handler exactly as AWS would.
The `transform-lambda` subproject is a plain Java library + `RequestHandler<S3Event, …>` entry point (deployable to Lambda):
secure StAX parsing (DTD/XXE disabled), handles USPTO bulk files that are *many XML documents concatenated*
(`<?xml …?>` repeated), formats: `us-patent-grant`, `us-patent-application-publication`, legacy `PATDOC` (2001–2004 SGML-ish),
and `trademark-applications-daily`. Output JSON lines written to the processed bucket, then loaded via odp-service
internal bulk-upsert in batches of 500.

JWT required (ROLE_USER); users see their own jobs, ADMIN sees all.

| Method & path | Description |
|---|---|
| `POST /api/v1/ingest/uploads` | multipart `files` (1–10 `.xml` or `.zip` of xml, ≤ 50 MB each) → 202 `[IngestJobResponse]` |
| `GET /api/v1/ingest/jobs` | `status?`, paging → page of `IngestJobResponse` |
| `GET /api/v1/ingest/jobs/{id}` | `IngestJobResponse` with `stages` and first 200 `errors` |
| `GET /api/v1/ingest/jobs/{id}/json` | transformed JSON download (array) |
| `POST /api/v1/ingest/jobs/{id}/retry` | re-run from the raw object (FAILED/PARTIAL only) → 202 |
| `DELETE /api/v1/ingest/jobs/{id}` | delete job + objects (owner/admin) → 204 |
| `GET /api/v1/ingest/samples` (public) | `[{ name, description, format, sizeBytes }]` |
| `GET /api/v1/ingest/samples/{name}` (public) | sample XML download |
| `GET /api/v1/ingest/stats` | `{ jobs, byStatus:[{value,count}], recordsLoaded, lastJobAt }` |

`IngestJobResponse`: `{ id (UUID), fileName, sizeBytes, status: QUEUED|PARSING|TRANSFORMED|LOADING|COMPLETED|PARTIAL|FAILED,
documentFormat: US_PATENT_GRANT|US_PATENT_APPLICATION|PATDOC_LEGACY|TRADEMARK_DAILY|UNKNOWN, recordsTotal, recordsLoaded,
recordsFailed, errors:[{ index, identifier, message }], stages:[{ stage: UPLOADED|PARSED|TRANSFORMED|LOADED, status, at, message }],
rawObjectKey, jsonObjectKey, ownerId, createdAt, startedAt, finishedAt, durationMs }`

Sample XML files (checked in under `ingest-service/samples/`): a small grant file (5 concatenated documents), an application
publication file, a legacy PATDOC file, a trademark daily file, and one deliberately malformed file.

## gateway (8080) — API Gateway equivalent (Spring Cloud Gateway Server WebMVC)

| Route | Target | Auth / limits |
|---|---|---|
| `/api/v1/patents/**`, `/api/v1/trademarks/**`, `/api/v1/stats/**`, `/api/v1/datasets/**` | odp 8081 | API key or JWT or anonymous (IP) — rate limited by tier |
| `/api/v1/auth/**`, `/api/v1/account/**`, `/api/v1/admin/**`, `/.well-known/**` | odp 8081 | auth endpoints limited per IP (10/min on login/register) |
| `/api/v1/fees/**` | fee 8082 | same tiering as data routes |
| `/api/v1/ingest/**` | ingest 8083 | JWT required (samples public) |
| `/v3/api-docs/{odp,fees,ingest}` | each service `/v3/api-docs` | public; aggregated Swagger UI at `/swagger-ui.html` |
| `/internal/**` | — | always 404 |

API key: header `X-API-Key` (or query `api_key`). Present + invalid → 401 ProblemDetail. Verified via odp internal verify,
cached (Caffeine, 60s, negative results 10s). Rate limiting: Bucket4j in-memory, per-minute + per-day bandwidth per identity
(`key:<id>`, `user:<sub>`, `ip:<addr>`); every response on limited routes carries `X-RateLimit-Limit`, `X-RateLimit-Remaining`,
`X-RateLimit-Reset`; 429 ProblemDetail with `Retry-After`. Usage counts per API key are batched and flushed every 30s to
`POST /internal/v1/usage`. `GET /gateway/status` → `{ services:[{ name, url, status:"UP"|"DOWN", latencyMs }] }` (public).
Adds/propagates `X-Request-Id`, strips inbound `X-Internal-Token`, per-route timeouts, access log line per request
(method, path, status, ms, identity, requestId).

## Web (Angular 22, ZardUI, Tailwind 4) — `web/`

Standalone components, signals, lazy routes, `HttpClient` + functional interceptors (JWT from storage, `X-Request-Id`,
error toast), dev proxy `/api`, `/v3`, `/.well-known`, `/gateway`, `/swagger-ui*` → `http://localhost:8080`.
Pages: landing, patents search (+ facets) & detail, trademarks search & detail (TSDR-style), fee calculator
(patent filing / maintenance / trademark tabs, itemized breakdown, save & share quote), fee schedule table (USPTO-page style,
schedule switcher), data pipeline (upload, samples, jobs list with polling, job detail with stages/errors/JSON),
developer portal (API keys create/rotate/revoke with one-time reveal, usage chart, code snippets, embedded API docs),
system status, admin (users, all jobs), auth (login/register), 404. Dark mode, responsive, accessible.
