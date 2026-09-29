-- ingest-service schema (owned exclusively by this service)

CREATE TABLE ingest_job (
    id               UUID          PRIMARY KEY,
    owner_id         VARCHAR(100)  NOT NULL,
    file_name        VARCHAR(512)  NOT NULL,
    size_bytes       BIGINT        NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    document_format  VARCHAR(30)   NOT NULL DEFAULT 'UNKNOWN',
    records_total    INTEGER       NOT NULL DEFAULT 0,
    records_loaded   INTEGER       NOT NULL DEFAULT 0,
    records_failed   INTEGER       NOT NULL DEFAULT 0,
    raw_bucket       VARCHAR(100)  NOT NULL,
    raw_object_key   VARCHAR(1024) NOT NULL,
    raw_etag         VARCHAR(100),
    json_bucket      VARCHAR(100),
    json_object_key  VARCHAR(1024),
    message          VARCHAR(2000),
    attempts         INTEGER       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    started_at       TIMESTAMPTZ,
    finished_at      TIMESTAMPTZ,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_ingest_job_status CHECK (status IN
        ('QUEUED', 'PARSING', 'TRANSFORMED', 'LOADING', 'COMPLETED', 'PARTIAL', 'FAILED')),
    CONSTRAINT ck_ingest_job_counts CHECK (records_total >= 0 AND records_loaded >= 0 AND records_failed >= 0)
);

CREATE INDEX ix_ingest_job_owner_created ON ingest_job (owner_id, created_at DESC);
CREATE INDEX ix_ingest_job_status_updated ON ingest_job (status, updated_at);
CREATE INDEX ix_ingest_job_created ON ingest_job (created_at DESC);

CREATE TABLE ingest_job_stage (
    id       BIGSERIAL     PRIMARY KEY,
    job_id   UUID          NOT NULL REFERENCES ingest_job (id) ON DELETE CASCADE,
    stage    VARCHAR(20)   NOT NULL,
    status   VARCHAR(20)   NOT NULL,
    message  VARCHAR(2000),
    at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_ingest_job_stage_job ON ingest_job_stage (job_id, at);

CREATE TABLE ingest_job_error (
    id            BIGSERIAL     PRIMARY KEY,
    job_id        UUID          NOT NULL REFERENCES ingest_job (id) ON DELETE CASCADE,
    record_index  INTEGER       NOT NULL,
    identifier    VARCHAR(200),
    message       VARCHAR(2000) NOT NULL,
    phase         VARCHAR(20)   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_ingest_job_error_job ON ingest_job_error (job_id, record_index);