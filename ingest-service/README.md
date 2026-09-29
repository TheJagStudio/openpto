# ingest-service (port 8083)

Legacy USPTO bulk XML → neutral JSON → Open Data Portal. Locally the service emulates the AWS pipeline
**S3 bucket → S3 event notification → Lambda → database**; every piece maps 1:1 onto an AWS service.

```
upload (multipart) ──► raw bucket  ──ObjectCreated──►  transform-lambda  ──►  processed bucket  ──►  odp bulk-upsert
  POST /uploads        openpto-raw     (event)         LambdaHandler           openpto-processed     (batches of 500)
  202 + job ids        jobs/{id}/f.xml                  S3Event → JSON          jobs/{id}/f.xml.json   → Aurora (odp schema)
```

## Layout

| Path | What |
|---|---|
| `transform-lambda/` | Plain Java 21 library, **no Spring**: secure StAX parsing, concatenated-file splitter, format detection, mappers, `LambdaHandler implements RequestHandler<S3Event, TransformResult>`. `:transform-lambda:lambdaZip` builds the deployment zip. |
| `src/main/java/.../storage` | `ObjectStorage` (put/get/stream/head/delete/list) with `LocalFsObjectStorage` (buckets = folders) and `S3ObjectStorage` (profile `aws`). |
| `src/main/java/.../service` | Upload validation (sniffing, safe zip extraction), job state machine, async pipeline, batch loader. |
| `src/main/java/.../client` | `OdpClient` — `RestClient` with timeouts and bounded exponential retry. |
| `samples/` | Sample USPTO files (also served by `GET /api/v1/ingest/samples`). |

## Local ↔ AWS mapping

| Local (this repo) | AWS |
|---|---|
| `LocalFsObjectStorage`, folders `../data/buckets/openpto-raw` and `openpto-processed` | Two S3 buckets (SSE-KMS, private) |
| Put into the raw bucket publishes `ObjectCreatedEvent {bucket,key,size,eTag}` via `ApplicationEventPublisher` | S3 event notification `s3:ObjectCreated:*` on the raw bucket |
| `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` on a bounded `ThreadPoolTaskExecutor` (2–4 threads, queue 100, caller-runs when full) | Lambda async invocation with reserved concurrency |
| `TransformInvoker` builds a real `S3Event` and calls `LambdaHandler.handleRequest` (also exposed as the Spring Cloud Function `transform`) | Lambda runtime calling `gov.openpto.ingest.transform.lambda.LambdaHandler::handleRequest` |
| `StorageObjectIO` (ObjectReader/ObjectWriter over the local buckets) | `S3ObjectIO` (S3 GetObject / PutObject, packaged by `lambdaZip`) |
| `RecordLoader` + `OdpClient` → `POST /internal/v1/{patents,trademarks}/bulk-upsert` | A loader Lambda (or the service polling an SQS queue fed by the processed bucket) writing to **Aurora PostgreSQL** through odp-service |
| `ingest` schema: `ingest_job`, `ingest_job_stage`, `ingest_job_error` | Same schema in Aurora (or DynamoDB for job state) |
| `StartupRecovery` fails jobs stuck > 10 min | Lambda retries + DLQ / CloudWatch alarm on stuck jobs |

With profile `aws` the service only uploads to S3 and serves job state; S3 itself triggers the Lambda. Build the
container with the SDK: `docker build --build-arg GRADLE_ARGS=-Paws .` (locally the AWS SDK is compile-only).

## Formats

| Format | Root element | Notes |
|---|---|---|
| `US_PATENT_GRANT` | `us-patent-grant` (ICE v4.x) | publication/application references, CPC (main + further), inventors, assignees, examiner/art unit, citations (examiner vs applicant), abstract, claims with `claim-ref` dependencies, term extension → expiration date |
| `US_PATENT_APPLICATION` | `us-patent-application` / `us-patent-application-publication` | `patentNumber` = publication number (`US20240123456A1`), status `PENDING`; assignee taken from `us-applicant[@applicant-authority-category=assignee]` when there is no `assignees` block |
| `PATDOC_LEGACY` | `PATDOC` (2001–2004 red book, ST.32 v2.4/2.5) | B110 number, B130 kind, B140 grant date, B210/B220 application, B540 title, B560 citations, B721 inventors, B731 assignees, B746 examiner, SDOAB abstract, SDOCL claims (`CLREF`) |
| `TRADEMARK_DAILY` | `trademark-applications-daily` | streamed `case-file` by `case-file`: serial/registration numbers, mark, status code → status, drawing code → mark type, filing basis, goods & services per Nice class, classifications, events, current owner |

Bulk files are **many XML documents concatenated** (`<?xml …?>` repeated). The splitter streams line by line and
hands each document to StAX separately (a DOCTYPE without a preceding XML declaration also starts a new document).
A document that is not well-formed, has an unsupported root, or misses a required field becomes a per-record error
(`index`, `identifier`, `message`) — it never aborts the file.

**XML security**: `SUPPORT_DTD=false`, `IS_SUPPORTING_EXTERNAL_ENTITIES=false`, no entity replacement, external
DTD/schema access disabled and a resolver that refuses everything. In addition every per-document DOCTYPE (including
SGML-style `PUBLIC "id" [ … ]` subsets that are not valid XML) is stripped before parsing, and named entities are
mapped from a small allow-list (`&mdash;`, `&lsquo;`, …) or dropped. Tests prove file-entity, external-DTD,
parameter-entity and billion-laughs payloads are not resolved.

## API (JWT required unless noted)

| Method & path | |
|---|---|
| `POST /api/v1/ingest/uploads` | multipart `files` (1–10 × `.xml` or `.zip` of xml, ≤ 50 MB each) → 202 `[IngestJobResponse]` |
| `GET /api/v1/ingest/jobs?status=&page=&size=&sort=createdAt,desc` | own jobs (ADMIN: all), contract page shape |
| `GET /api/v1/ingest/jobs/{id}` | detail with stages and first 200 errors (403 for another user's job) |
| `GET /api/v1/ingest/jobs/{id}/json` | streamed JSON array (attachment) |
| `POST /api/v1/ingest/jobs/{id}/retry` | FAILED/PARTIAL only (409 otherwise) → 202 |
| `DELETE /api/v1/ingest/jobs/{id}` | owner/ADMIN → 204, deletes raw + processed objects |
| `GET /api/v1/ingest/stats` | `{ jobs, byStatus, recordsLoaded, lastJobAt }` |
| `GET /api/v1/ingest/samples`, `/samples/{name}` | public |

Swagger UI: http://localhost:8083/swagger-ui.html · health: `/actuator/health/{liveness,readiness}`.

Upload hardening: extension **and** sniffed content must agree; zip entries are checked for zip slip (`..`,
absolute, drive paths — entry names are never used as paths), and extraction enforces max entries, max bytes per
entry, max total bytes and a max compression ratio on the bytes actually inflated.

Job status: `QUEUED → PARSING → TRANSFORMED → LOADING → COMPLETED | PARTIAL | FAILED`. `PARTIAL` = some records
failed (transform or odp rejected them); odp unavailable after 3 attempts (5xx/429/connection errors, 500 ms × 2ⁿ
back-off) → `FAILED` (or `PARTIAL` if earlier batches were loaded). Retry re-delivers the ObjectCreated event for
the raw object.

## Run

```powershell
. D:\JavaSpring\tools\env.ps1
.\gradlew.bat build                         # tests + JaCoCo (build/reports/jacoco, transform-lambda/build/reports/jacoco)
.\gradlew.bat :transform-lambda:lambdaZip   # transform-lambda/build/distributions/openpto-transform-lambda-*.zip
.\gradlew.bat bootRun                       # port 8083; needs odp-service on 8081 for JWKS and loading
docker build -t openpto/ingest-service .
```

Environment: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `ODP_BASE_URL` (default `http://localhost:8081`), `INTERNAL_TOKEN`
(default `dev-internal-token-change-me`), `JWKS_URI`, `STORAGE_ROOT` (default `../data/buckets`), `PORT`.
Tests use profile `test` → `openpto_test` (override `TEST_DB_URL`), schema `ingest` only.
