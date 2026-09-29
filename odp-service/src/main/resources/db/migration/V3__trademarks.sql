-- Trademarks (TSDR-style) with goods & services by Nice class and a prosecution history.
-- nice_classes is a denormalized int[] (maintained by the writer) so list pages and the
-- niceClass filter never need the child table; goods_text feeds the full-text vector.

CREATE TABLE trademarks (
    id                  BIGSERIAL    PRIMARY KEY,
    serial_number       VARCHAR(16)  NOT NULL,
    registration_number VARCHAR(16),
    mark_text           VARCHAR(500) NOT NULL,
    mark_type           VARCHAR(24)  NOT NULL,
    status              VARCHAR(24)  NOT NULL,
    filing_date         DATE         NOT NULL,
    registration_date   DATE,
    status_date         DATE,
    owner_name          VARCHAR(300),
    owner_address       VARCHAR(500),
    attorney            VARCHAR(200),
    filing_basis        VARCHAR(4),
    nice_classes        INTEGER[]    NOT NULL DEFAULT '{}',
    goods_text          TEXT,
    source              VARCHAR(8)   NOT NULL,
    ingest_job_id       VARCHAR(64),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0,
    search_vector       TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english'::regconfig, coalesce(mark_text, '')), 'A') ||
        setweight(to_tsvector('english'::regconfig, coalesce(owner_name, '')), 'B') ||
        setweight(to_tsvector('english'::regconfig, coalesce(goods_text, '')), 'C')
    ) STORED,
    CONSTRAINT uk_trademarks_serial UNIQUE (serial_number),
    CONSTRAINT ck_trademarks_mark_type CHECK (mark_type IN ('STANDARD_CHARACTER', 'DESIGN', 'SOUND')),
    CONSTRAINT ck_trademarks_status CHECK (status IN ('LIVE_PENDING', 'LIVE_REGISTERED', 'DEAD_ABANDONED', 'DEAD_CANCELLED')),
    CONSTRAINT ck_trademarks_basis CHECK (filing_basis IS NULL OR filing_basis IN ('1A', '1B', '44D', '44E', '66A')),
    CONSTRAINT ck_trademarks_source CHECK (source IN ('SEED', 'INGEST'))
);

CREATE INDEX ix_trademarks_search ON trademarks USING GIN (search_vector);
CREATE INDEX ix_trademarks_mark_trgm ON trademarks USING GIN (lower(mark_text) public.gin_trgm_ops);
CREATE INDEX ix_trademarks_owner_trgm ON trademarks USING GIN (lower(owner_name) public.gin_trgm_ops);
CREATE INDEX ix_trademarks_nice_classes ON trademarks USING GIN (nice_classes);
CREATE INDEX ix_trademarks_status ON trademarks (status);
CREATE INDEX ix_trademarks_filing_date ON trademarks (filing_date DESC, id DESC);
CREATE INDEX ix_trademarks_registration_date ON trademarks (registration_date DESC NULLS LAST, id DESC);
CREATE INDEX ix_trademarks_registration_number ON trademarks (registration_number);
CREATE INDEX ix_trademarks_source_updated ON trademarks (source, updated_at DESC);

CREATE TABLE trademark_goods_services (
    trademark_id BIGINT  NOT NULL REFERENCES trademarks (id) ON DELETE CASCADE,
    seq          INTEGER NOT NULL,
    nice_class   INTEGER NOT NULL,
    description  TEXT    NOT NULL,
    PRIMARY KEY (trademark_id, seq),
    CONSTRAINT ck_tm_goods_class CHECK (nice_class BETWEEN 1 AND 45)
);

CREATE TABLE trademark_events (
    trademark_id BIGINT       NOT NULL REFERENCES trademarks (id) ON DELETE CASCADE,
    seq          INTEGER      NOT NULL,
    event_date   DATE         NOT NULL,
    code         VARCHAR(16)  NOT NULL,
    description  VARCHAR(500) NOT NULL,
    PRIMARY KEY (trademark_id, seq)
);

CREATE INDEX ix_trademark_events_date ON trademark_events (event_date);
