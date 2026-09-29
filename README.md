# OpenPTO — legacy patent & trademark data modernization

A free, open, SaaS-style portal (no payments) that modernizes legacy USPTO-style data access — the
equivalents of the USPTO Open Data Portal (ODP) and TSDR — as a set of Spring Boot microservices and an
Angular UI. Everything runs locally; every piece is shaped to map 1:1 onto AWS.

[![OpenPTO – Legacy Patent & Trademark Data Modernization Platform](https://img.youtube.com/vi/Asn0EuXNEA0/maxresdefault.jpg)](https://www.youtube.com/watch?v=Asn0EuXNEA0)

▶️ **[Watch the demo on YouTube](https://www.youtube.com/watch?v=Asn0EuXNEA0)** — patent & trademark search, fee calculator,
XML → JSON pipeline, developer portal, system status and admin console.

| Project brief | Where it lives |
|---|---|
| **Public Open Data Portal behind an API gateway** — REST API over mock patent/trademark data in PostgreSQL, API keys, rate limiting, generated OpenAPI docs | `odp-service` (data, accounts, keys) + `gateway` (keys, tiers, throttling, aggregated Swagger) |
| **Legacy XML → JSON pipeline** — upload XML to a bucket, event triggers a Lambda that parses, transforms and loads into the DB | `ingest-service` (bucket + S3-shaped events) + `ingest-service/transform-lambda` (the Lambda) |
| **Decoupled fee-calculation microservice** — entity size, claims, late fees; Angular calculator; exhaustive JUnit/Mockito tests | `fee-service` (+ `legacy-fee-monolith` characterization tests) and `web` `/fees` |

```
browser ──► web (Angular 22 + ZardUI + Tailwind 4, :4200)
              │  /api, /v3, /gateway  (dev proxy / nginx)
              ▼
           gateway :8080 ── API key / JWT / IP identity → Bucket4j tiers → routes, circuit breaker, usage metering
              ├──► odp-service :8081   patents, trademarks, search, exports, auth (RS256 JWT + JWKS), API keys   [schema odp]
              ├──► fee-service :8082   versioned fee schedules, calculators, saved quotes                      [schema fees]
              └──► ingest-service :8083 uploads → bucket → ObjectCreated event → transform-lambda → odp bulk upsert [schema ingest]
                                                   PostgreSQL 18 :5433 (Aurora stand-in)
```

Full API/DB contract: [docs/CONTRACTS.md](docs/CONTRACTS.md). Each service has its own README with design notes.

## Run it

Prerequisite: the portable toolchain in `D:\JavaSpring\tools` (JDK 21, Node 24, PostgreSQL 18 — see its README).

```powershell
.\dev.ps1 up          # Postgres + 4 services + Angular dev server, each in its own window
.\scripts\smoke.ps1   # end-to-end checks through the gateway
.\dev.ps1 down
```

- Web: http://localhost:4200 — sign up for a free account (no payment), or use the dev admin `admin@openpto.local` (password in `odp-service/src/main/resources/application.yml`, dev only).
- API docs (all services, "Try it out" goes through the gateway): http://localhost:8080/swagger-ui.html
- Try the API: `curl "http://localhost:8080/api/v1/patents?q=battery&size=3"` (anonymous tier) or add `-H "X-API-Key: opto_..."` with a key from the Developers page.

With Docker Desktop instead: `docker compose up --build` (same ports).

## Build & test

```powershell
.\dev.ps1 build       # every service: compile + tests + JaCoCo gates; web: build + tests
```

| Component | Tests | Coverage gate |
|---|---|---|
| fee-service | 515 (parameterized fee tables, boundaries, property checks, legacy characterization over 10k+ inputs) | domain ≥ 90% (actual 100% lines) |
| odp-service | 127 (services, MVC slices, Postgres integration: auth → keys → verify → usage, FTS search, bulk upsert, exports) | service ≥ 80% (98%) |
| ingest-service | 166 (all 4 XML formats, XXE/billion-laughs, 5k-doc streaming, pipeline states, odp client retries) | ≥ 80% (95%) |
| gateway | 70 (tiers, identity, cache, 429/401/502/504, stub downstream integration) | ≥ 80% (94%) |
| web | 62 (API services, interceptors, guards, auth store, calculator, search state) | — |

DB-backed tests use the `openpto_test` database on localhost:5433 (CI uses a Postgres service container).

## AWS mapping (reference only — nothing is deployed)

| Local | AWS | Notes |
|---|---|---|
| gateway | API Gateway REST API, usage plans + API keys, throttling | `infra/terraform` usage plan mirrors the FREE tier |
| odp / fee / ingest services | ECS Fargate behind an internal ALB (VPC link) | Dockerfiles in each service |
| PostgreSQL 18 | Aurora PostgreSQL Serverless v2 | credentials via Secrets Manager |
| `data/buckets/openpto-raw` / `-processed` | S3 buckets | `S3ObjectStorage` under Spring profile `aws` |
| ObjectCreated event → `LambdaHandler` | S3 event notification → Lambda (Java 21, SnapStart) | `.\gradlew.bat :transform-lambda:lambdaZip` |
| web `dist/` | S3 + CloudFront | SPA fallback to index.html |
| Actuator / logs | CloudWatch metrics, logs, alarms | `/actuator/prometheus`, request-id in every log line |

See `infra/terraform/` (not applied).

## Layout

```
fee-service/        Spring Boot · domain core (no Spring) · legacy-fee-monolith/ subproject
odp-service/        Spring Boot · search, seed generator, identity, API keys, internal APIs
ingest-service/     Spring Boot · storage, pipeline · transform-lambda/ subproject · samples/
gateway/            Spring Cloud Gateway Server WebMVC · rate limiting · aggregated OpenAPI
web/                Angular 22 · ZardUI (zard-cli) · Tailwind 4 · Vitest
docs/CONTRACTS.md   API/DB contract
infra/terraform/    AWS reference infrastructure
scripts/smoke.ps1   end-to-end smoke test
dev.ps1             local runner
docker-compose.yml  optional container run
```
