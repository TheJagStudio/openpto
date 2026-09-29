# odp-service — OpenPTO Open Data Portal (port 8081)

Patent and trademark search (PostgreSQL full-text), streamed bulk datasets, portal accounts,
API keys and usage, and the **identity provider** for the platform (RS256 JWTs + JWKS).
Contract: [`../docs/CONTRACTS.md`](../docs/CONTRACTS.md) (odp-service and Identity sections).

Java 21 · Spring Boot 4.1.1 (Spring Framework 7, Security 7, Hibernate 7, Jackson 3) · Flyway ·
MapStruct · Caffeine · springdoc 3.1.

## Run

```powershell
. D:\JavaSpring\tools\env.ps1
cd D:\JavaSpring\projects\openpto\odp-service
.\gradlew.bat bootRun --args="--spring.profiles.active=dev"   # or:
.\gradlew.bat bootJar; java -jar build\libs\odp-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

The `dev` profile seeds ~5,000 patents and ~2,000 trademarks on first start (only when the tables are
empty; about 6 s + 1.3 s) and creates the admin `admin@openpto.local` / `ADMIN_PASSWORD` (default `Admin#12345`).

- Swagger UI: http://localhost:8081/swagger-ui.html · OpenAPI (3.0.1): http://localhost:8081/v3/api-docs
- JWKS: http://localhost:8081/.well-known/jwks.json
- Probes: `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info` (public);
  `/actuator/metrics`, `/actuator/prometheus` need ROLE_ADMIN.

### Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5433/openpto` / `openpto` / `openpto` | Database (schema `odp`, created by Flyway) |
| `DB_POOL_SIZE` | `15` | Hikari maximum pool size |
| `PORT` | `8081` | HTTP port |
| `JWT_KEY_DIR` | `../data/keys` | RSA signing key (`odp-jwt-private.pem`, generated on first start; keep it to keep tokens valid) |
| `INTERNAL_TOKEN` | `dev-internal-token-change-me` | Shared secret for `/internal/**` (`X-Internal-Token`) |
| `ADMIN_SEED_ENABLED` / `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `true` / `admin@openpto.local` / `Admin#12345` | Admin bootstrap |
| `SEED_ENABLED` | `false` (`true` in `dev`) | Mock data seed |

`pg_trgm` is created by migration V1 (`CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public`).
It is a *trusted* extension, so the database owner (`openpto`) can create it — no superuser needed.

## Layout

```
gov.openpto.odp
├── config       AppProperties (typed app.*), SecurityConfig (2 chains), JwtConfig, OpenApiConfig,
│                RequestIdFilter (X-Request-Id + MDC), PostgresFunctionContributor (FTS functions), Clock
├── controller   Patent, Trademark, Stats, Dataset, Auth, Account, Admin, Internal, Jwks
├── dto          request/response records (@Schema examples), PageResponse (contract page shape)
├── exception    domain exceptions + GlobalExceptionHandler (RFC 7807 + requestId + errors[])
├── mapper       MapStruct: PatentMapper, TrademarkMapper, AccountMapper
├── model        JPA entities (User, ApiKey, Patent, Trademark + embeddable children)
├── repository   Spring Data repos, JDBC writers (seed/upsert), UsageRepository, ExportRepository (cursor)
│   └── spec     Specifications (search filters/sort) + SqlFilters (same filters as SQL for exports)
├── security     JwtKeyProvider (persisted RSA key), InternalTokenFilter, ProblemResponseWriter
├── seed         MockDataGenerator (deterministic), SeedVocabulary, SeedRunner
└── service      Auth, Token, ApiKey(+Generator), Usage, Patent, Trademark, Stats, BulkUpsert,
                 RecordNormalizer, Export, AdminUser, AdminBootstrap
```

## Migrations (`src/main/resources/db/migration`)

| Version | Contents |
|---|---|
| `V1__identity.sql` | `pg_trgm`; `roles`, `users` (lockout fields: `failed_login_count`, `locked_until`), `user_roles`, `api_keys` (SHA-256 `key_hash` unique, `prefix`, `tier`, `revoked_at`, `last_used_at`, `request_count`), `api_usage_daily` (PK `key_id, usage_date`) |
| `V2__patents.sql` | `patents` (filter columns + generated weighted `search_vector` over title/assignees/inventors/abstract, GIN; trigram on title; btree on type/status/dates/CPC), `patent_claims`, `patent_inventors`, `patent_assignees` (trigram on names), `patent_cpc` (prefix index), `patent_citations` |
| `V3__trademarks.sql` | `trademarks` (`search_vector` over mark/owner/goods, trigram on mark and owner, GIN on `nice_classes int[]`), `trademark_goods_services`, `trademark_events` |

## Design notes

- **Search**: Specifications build the filters; full text goes through Hibernate functions registered
  in `PostgresFunctionContributor` — `fts_match` → `search_vector @@ websearch_to_tsquery('english', :q)`
  (GIN-indexed) and `fts_rank` → `ts_rank_cd(...)` for `sort=relevance` (the default when `q` is present,
  otherwise `filingDate,desc`; ties broken by id). CPC is a prefix match on any of the patent's codes;
  assignee/inventor/owner are case-insensitive contains (trigram-indexed).
- **No N+1**: child collections are LAZY with `@BatchSize`; a result page costs 4 statements
  (count, page, assignees, inventors) — asserted by `DataFlowIT`.
- **Writes** go through JDBC (`PatentWriteRepository`/`TrademarkWriteRepository`): the seed uses
  batch inserts with pre-allocated ids; the bulk upsert uses `INSERT ... ON CONFLICT (natural key) DO
  UPDATE ... RETURNING (xmax = 0)` and replaces child rows. JPA entities are used for reads.
- **Exports** stream from a server-side cursor (fetch size 500) straight to the response
  (`StreamingResponseBody`); nothing is materialized. CSV cells are RFC 4180-escaped and protected
  against spreadsheet formula injection.
- **Caching**: `/stats` and `/patents/facets` are cached in Caffeine for 60 s and evicted on every
  successful bulk upsert.

## Notes for the gateway and ingest-service

- **JWT**: RS256, `iss=openpto`, `kid` = RFC 7638 thumbprint (stable across restarts while the key
  file is kept). Claims `sub` (user UUID), `email`, `name`, `roles` (`["USER"]` / `["USER","ADMIN"]`), `jti`.
  Validate with `jwk-set-uri: http://localhost:8081/.well-known/jwks.json` (Cache-Control `max-age=300`).
  The issuer is not a URL — compare the `iss` claim as a string.
- **`POST /internal/v1/api-keys/verify`**: format-checked then a single unique-index hash lookup.
  Invalid → `{"valid":false, "keyId":null, ...}` (HTTP 200). Keys of ADMIN users get tier `ADMIN`,
  all others `FREE`.
- **`POST /internal/v1/usage`**: 204. Duplicate `(keyId, date)` entries are summed; unknown key ids
  are skipped silently (never fail the flush). Also bumps `api_keys.request_count` / `last_used_at`.
- **Bulk upsert** (`/internal/v1/patents|trademarks/bulk-upsert`, ≤ 1000 per call, else 400):
  every record is parsed, validated and written in its **own transaction**; malformed JSON values
  (e.g. unknown enum), constraint violations and database rejections go to `failed[]` as
  `{id, error}` where `id` is the natural key (or `#index`). `source` is always set to `INGEST`.
  Derived when omitted: `status` (grant date → GRANTED/EXPIRED, else PENDING), `expirationDate`
  (filing + 20 y; design: grant + 15 y), `primaryCpc` (first code; codes are upper-cased and spaces
  removed), claim `independent` (= no `dependsOn`), trademark `markType` (STANDARD_CHARACTER),
  `status` (registration date → LIVE_REGISTERED, else LIVE_PENDING) and `statusDate` (last event).
  Records with only `applicationNumber` are keyed as `USAPP` + its digits (e.g. `17/123,456` →
  `USAPP17123456`). `ingestJobId` is free text (≤ 64 chars). Claims must reference an earlier claim.
- **Errors**: `application/problem+json` with `type`, `title`, `status`, `detail`, `instance`,
  `requestId`, and `errors:[{field,message}]` for validation. `X-Request-Id` is honoured (or generated)
  and echoed. Login lockout (5 failures → 15 min) answers **429** with `Retry-After`.

## Tests

```powershell
.\gradlew.bat build        # unit + slice + integration tests, JaCoCo gate (service package >= 80% lines)
```

Integration tests (`*IT`) use profile `test` against `openpto_test` (override with `TEST_DB_URL`),
touch only schema `odp`, create uniquely named rows and delete them afterwards, so they are safe to
re-run and to run alongside other services' tests. Coverage report: `build/reports/jacoco/test/html`.

## Docker

```bash
docker build -t openpto/odp-service .
docker run -p 8081:8081 -e DB_URL=jdbc:postgresql://host.docker.internal:5433/openpto \
  -v openpto-keys:/data/keys openpto/odp-service
```

Multi-stage (Temurin 21 JDK → JRE), layered jar, runs as a non-root user; the JWT key lives in the
`/data/keys` volume.
