# OpenPTO gateway (port 8080)

The single entry point for the web app and API consumers. Built on **Spring Cloud Gateway Server WebMVC**
(Spring Boot 4.1, Java 21, virtual threads). Locally it plays the role that **Amazon API Gateway** plays in AWS:
API keys, usage plans, throttling/quotas, a JWT authorizer, and request routing to the services.

```
.\gradlew.bat build          # tests + JaCoCo (build/reports/jacoco/test/html), fails under 80% line coverage
.\gradlew.bat bootRun        # http://localhost:8080  (Swagger UI: /swagger-ui.html, status: /gateway/status)
docker build -t openpto/gateway .
```

## Mapping to AWS API Gateway

| AWS API Gateway | Here |
|---|---|
| Resources / routes + HTTP integrations | `RoutesConfig` (Java DSL `RouterFunction`s, `HandlerFunctions.http()`) |
| API keys (`x-api-key`) | `X-API-Key` header or `api_key` query param, verified by odp-service `POST /internal/v1/api-keys/verify`, cached (Caffeine: valid 60s, invalid 10s) |
| Usage plans (throttle + quota) | Tiers below: Bucket4j bucket per identity with a per-minute greedy refill (throttle) and a per-day bandwidth (quota) |
| Usage plan metering / `GetUsage` | `UsageMeter` counts requests per key per UTC day, flushes to odp-service `POST /internal/v1/usage` every 30s, merges back on failure, flushes on shutdown |
| JWT authorizer | Bearer JWT validated locally against odp-service's JWKS (RS256, issuer `openpto`); required at the gateway only for `/api/v1/ingest/**` (samples public) |
| Resource policy / private integrations | `/internal/**` always 404; inbound `X-Internal-Token` is stripped |
| Integration timeouts (max 29s) | Per-route connect/read timeouts (`gateway.services.*`, `gateway.route-read-timeouts.*`) |
| `429 Too Many Requests` + throttling headers | ProblemDetail 429 + `Retry-After`, `X-RateLimit-*` on every limited response |
| Stage variables | Env vars `ODP_URL`, `FEE_URL`, `INGEST_URL`, `INTERNAL_TOKEN`, `JWKS_URI` |
| CORS config | Only here (`gateway.cors-allowed-origins`, default `http://localhost:4200`) |
| CloudWatch access logs | One line per request on logger `gov.openpto.gateway.access` |

In a real AWS deployment API Gateway would take over keys/quotas/throttling; this service would then shrink
to routing + aggregated docs (or disappear behind an ALB). Because limits are kept in memory, run a single
instance, or move buckets to Redis (Bucket4j has a `bucket4j-redis` backend) before scaling out.

## Tiers (per identity)

| Tier | Identified by | Bucket key | Per minute | Per day |
|---|---|---|---|---|
| `ANONYMOUS` | client IP | `ip:<addr>` | 30 | 1,000 |
| `FREE` | API key (limits returned by verify win) | `key:<keyId>` | 120 | 20,000 |
| `WEB` | valid user JWT, no key | `user:<sub>` | 300 | 50,000 |
| `ADMIN` | JWT with role `ADMIN` | `user:<sub>` | 1,200 | 1,000,000 |
| login/register | client IP, `POST /api/v1/auth/login|register` | `auth-ip:<addr>` | 10 | – |

Resolution order on data + fee routes: API key → bearer JWT → client IP. A key that is present but invalid
returns **401** (even if a JWT is also sent); an invalid/expired JWT on these routes returns **401** with
`WWW-Authenticate: Bearer error="invalid_token"` so clients learn their session expired.
`X-Forwarded-For` is only believed when the direct peer is listed in `gateway.trusted-proxies` (default: none).

## Headers

| Header | Direction | Meaning |
|---|---|---|
| `X-Request-Id` | in/out/downstream | Correlation id; generated (UUID) if absent or unsafe; in MDC and every log line |
| `X-RateLimit-Limit` | out | Requests per minute for the caller's tier |
| `X-RateLimit-Remaining` | out | Tokens left (minimum of the minute and day windows) |
| `X-RateLimit-Reset` | out | Seconds until the minute window is full again |
| `Retry-After` | out (429/503) | Seconds to wait |
| `X-API-Key` | in | API key; stripped before forwarding (so is the `api_key` query param) |
| `X-Internal-Token` | in | Always stripped (internal calls only) |
| `X-Forwarded-For/Proto/Host/Port` | downstream | Rebuilt by the gateway; `Host` is preserved |

## Routes

| Path | Target | Notes |
|---|---|---|
| `/api/v1/patents/**`, `/trademarks/**`, `/stats/**` | odp-service | tiered limits (route `odp-data`, 30s) |
| `/api/v1/datasets/**` | odp-service | tiered limits, streamed CSV/JSON (route `odp-datasets`, 120s) |
| `/api/v1/auth/**`, `/account/**`, `/admin/**`, `/.well-known/**` | odp-service | login/register 10/min per IP |
| `/api/v1/fees/**` | fee-service | tiered limits |
| `/api/v1/ingest/**` | ingest-service | JWT required (samples public), multipart streamed (180s) |
| `/v3/api-docs/{odp,fees,ingest}` | each `/v3/api-docs` | `servers` rewritten to the gateway, `X-API-Key` scheme added |
| `/swagger-ui.html` | gateway | Swagger UI listing the three specs |
| `/gateway/status` | gateway | `{ services:[{ name, url, status, latencyMs }] }`, probes run in parallel (2s) |
| `/internal/**` | – | always 404 |

Errors the gateway originates are `application/problem+json`
(`{ type, title, status, detail, instance, requestId }`): 401 bad key/JWT, 404, 429, 502 service down
or verifier unreachable, 503 circuit open (5 consecutive transport failures → fail fast for 10s), 504 timeout.

## Request pipeline

1. `RequestIdFilter` (servlet filter, highest precedence): correlation id, MDC, access log.
2. Spring Security (-100): firewall (rejects `..`, `;`, encoded slashes), CORS, security headers,
   JWT on `/api/v1/ingest/**` and `/actuator/**` (metrics need ADMIN). Stateless, CSRF off.
3. `RateLimitFilter` (0): identity → bucket → `X-RateLimit-*` → 401/429/502, usage metering.
4. Router: `/internal/**` 404 → per-route `uri` + `preserveHostHeader` (+ strip `api_key`) → `DownstreamGuard`
   (circuit breaker, 502/503/504) → per-route JDK `HttpClient` (`RouteAwareProxyExchange`).
   `GatewayRequestHeadersFilter` / `GatewayResponseHeadersFilter` sanitise headers both ways.

## Configuration (`application.yml`, prefix `gateway`)

| Key | Default |
|---|---|
| `services.{odp,fees,ingest}.url` | `${ODP_URL:http://localhost:8081}`, `${FEE_URL:…8082}`, `${INGEST_URL:…8083}` |
| `services.*.connect-timeout` / `read-timeout` | 2s / 30s (odp), 15s (fees), 60s (ingest) |
| `route-read-timeouts.<routeId>` | `odp-datasets: 120s`, `ingest: 180s` |
| `internal-token` | `${INTERNAL_TOKEN:dev-internal-token-change-me}` |
| `trusted-proxies` | `${TRUSTED_PROXIES:}` (IPs/CIDRs) |
| `cors-allowed-origins` | `${CORS_ALLOWED_ORIGINS:http://localhost:4200}` |
| `public-base-url` | `${GATEWAY_PUBLIC_URL:}` (OpenAPI `servers`; empty = from request) |
| `rate-limit.tiers.<TIER>.per-minute/per-day`, `auth-per-minute`, `bucket-idle-expiry`, `max-buckets` | contract tiers, 10, 25h, 200000 |
| `api-keys.positive-ttl/negative-ttl/max-entries` | 60s / 10s / 50000 |
| `usage.flush-interval-ms/max-pending-entries/batch-size` | 30000 / 50000 / 1000 |
| `circuit-breaker.failure-threshold/open-duration` | 5 / 10s |
| `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | `${JWKS_URI:http://localhost:8081/.well-known/jwks.json}` |
