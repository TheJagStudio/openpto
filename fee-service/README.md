# fee-service (port 8082)

Decoupled USPTO-style fee calculator for OpenPTO: versioned fee schedules, patent filing /
maintenance / trademark quotes, and saved quotes. Owns Postgres schema `fees`. All endpoints are public
(the gateway applies API keys and rate limits). **All amounts are illustrative**, modeled on the USPTO
schedule effective 2025-01-19 (`FY2025`) and the prior one (`FY2023`, 2022-12-29 to 2025-01-18).

## Endpoints

| Method & path | Description |
|---|---|
| `GET /api/v1/fees/schedules` | Schedules, newest first; `current` = effective today (US Eastern) |
| `GET /api/v1/fees/schedules/{code\|current}?category=PATENT\|TRADEMARK` | Schedule with items (large/small/micro, unit) |
| `POST /api/v1/fees/patent/filing` | `PatentFilingRequest` -> `FeeQuote` |
| `POST /api/v1/fees/patent/maintenance` | `{ entitySize, grantDate, asOfDate?, paidStages? }` -> windows + `payableNow` |
| `POST /api/v1/fees/trademark` | `TrademarkFeeRequest` -> `FeeQuote` |
| `POST /api/v1/fees/quotes` | `{ kind, request, label? }` -> 201 saved quote (recomputed server-side) |
| `GET /api/v1/fees/quotes/{id}` | Saved quote, recomputed from the stored request |

Errors are RFC 7807 `application/problem+json` with `requestId` and, for validation, `errors[{field,message}]`.
`X-Request-Id` is read (or generated) and echoed. Ops: `/actuator/health` (+ `/liveness`, `/readiness`),
`/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`; docs at `/v3/api-docs` and `/swagger-ui.html`.

```bash
curl -s localhost:8082/api/v1/fees/patent/filing -H 'Content-Type: application/json' \
  -d '{"applicationType":"UTILITY","entitySize":"SMALL","totalClaims":25,"independentClaims":5}'
```

## Run

```powershell
. D:\JavaSpring\tools\env.ps1
.\gradlew.bat build          # compile, 500+ tests, JaCoCo report + >=90% line gate on the domain core
.\gradlew.bat bootRun        # needs Postgres at localhost:5433 (DB_URL / DB_USER / DB_PASSWORD override)
docker build -t openpto/fee-service .
```

Tests: unit tests need no DB; `FeeServiceIntegrationTest` uses profile `test` -> `openpto_test`
(`TEST_DB_URL` override), schema `fees`, migrated by Flyway. Coverage report: `build/reports/jacoco/test/html`.

## Design notes

- **Pure domain core** (`gov.openpto.fee.domain`): `PatentFilingCalculator`, `MaintenanceCalculator`,
  `TrademarkFeeCalculator` take an immutable `FeeSchedule` plus a normalized input and return a `FeeBreakdown`.
  No Spring, JPA or I/O, so every rule is unit-testable with in-memory schedules and the core could be lifted
  into a Lambda unchanged. Spring services only pick the schedule effective on the date and delegate.
- **Schedules are data, not code**: amounts live in `fee_item` rows (large/small/micro stored explicitly, no
  runtime percentage math), selected by effective date (inclusive range). They are loaded once and cached
  (`@Cacheable`), since they only change through migrations.
- **BigDecimal, scale 2, HALF_UP** everywhere: binary floating point cannot represent most cent values
  exactly and errors accumulate across line items. Totals are always derived from the line items, so
  `total == sum(lineItems)` holds by construction (and is property-tested).
- **Saved quotes store only the normalized request** (jsonb, defaults applied, schedule date pinned) and are
  recomputed on read: client totals are never trusted and stored totals can never drift from the rules.
- **Maintenance payment history**: without `paidStages`, windows whose grace period ended are assumed paid
  (`PAID_WINDOW_PASSED`); with it, an unpaid passed stage is `EXPIRED` and so is every later stage.

## Legacy monolith and characterization tests

`legacy-fee-monolith/` holds `LegacyFeeEngine`, the pre-extraction code (static methods, doubles, magic
numbers, one giant if/else, entity codes `"L"/"S"/"M"`). `LegacyCharacterizationTest` runs 10,000 seeded
random inputs, a ~8,000-case exhaustive boundary grid and 5,000+ maintenance dates through both engines and
asserts identical accept/reject decisions and totals to the cent.

**LEGACY-001 (intentionally fixed):** the monolith computed application-size blocks as
`(sheets - 100) / 50 + 1`, an off-by-one "ceiling" that overcharged one extra 50-sheet block whenever the
sheets over 100 were an exact multiple of 50 (150, 200, 250, ... sheets). The rule is "each additional 50
sheets *or fraction thereof*": `ceil((sheets - 100) / 50)`. The tests assert that on exactly those inputs the
legacy total is one `APP_SIZE` fee higher, and equal everywhere else.
