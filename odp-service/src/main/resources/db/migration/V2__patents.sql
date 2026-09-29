-- Patents and their child collections.
-- Filter columns are real, indexed columns; child tables are the source of truth for
-- parties/claims/CPC/citations, while assignee_text / inventor_text are denormalized
-- (maintained by the writer) so the generated tsvector can cover them.

CREATE TABLE patents (
    id                 BIGSERIAL    PRIMARY KEY,
    patent_number      VARCHAR(32)  NOT NULL,
    application_number VARCHAR(32),
    title              TEXT         NOT NULL,
    abstract_text      TEXT,
    type               VARCHAR(16)  NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    filing_date        DATE         NOT NULL,
    grant_date         DATE,
    priority_date      DATE,
    expiration_date    DATE,
    primary_cpc        VARCHAR(32),
    cpc_section        VARCHAR(1),
    examiner           VARCHAR(200),
    art_unit           VARCHAR(16),
    assignee_text      TEXT,
    inventor_text      TEXT,
    source             VARCHAR(8)   NOT NULL,
    ingest_job_id      VARCHAR(64),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,
    search_vector      TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english'::regconfig, coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english'::regconfig, coalesce(assignee_text, '')), 'B') ||
        setweight(to_tsvector('english'::regconfig, coalesce(inventor_text, '')), 'B') ||
        setweight(to_tsvector('english'::regconfig, coalesce(abstract_text, '')), 'C')
    ) STORED,
    CONSTRAINT uk_patents_number UNIQUE (patent_number),
    CONSTRAINT ck_patents_type CHECK (type IN ('UTILITY', 'DESIGN', 'PLANT', 'REISSUE')),
    CONSTRAINT ck_patents_status CHECK (status IN ('PENDING', 'GRANTED', 'ABANDONED', 'EXPIRED')),
    CONSTRAINT ck_patents_source CHECK (source IN ('SEED', 'INGEST'))
);

CREATE INDEX ix_patents_search ON patents USING GIN (search_vector);
CREATE INDEX ix_patents_title_trgm ON patents USING GIN (lower(title) public.gin_trgm_ops);
CREATE INDEX ix_patents_type ON patents (type);
CREATE INDEX ix_patents_status ON patents (status);
CREATE INDEX ix_patents_filing_date ON patents (filing_date DESC, id DESC);
CREATE INDEX ix_patents_grant_date ON patents (grant_date DESC NULLS LAST, id DESC);
CREATE INDEX ix_patents_cpc_section ON patents (cpc_section);
CREATE INDEX ix_patents_primary_cpc ON patents (primary_cpc varchar_pattern_ops);
CREATE INDEX ix_patents_application_number ON patents (application_number);
CREATE INDEX ix_patents_source_updated ON patents (source, updated_at DESC);

CREATE TABLE patent_claims (
    patent_id    BIGINT  NOT NULL REFERENCES patents (id) ON DELETE CASCADE,
    claim_number INTEGER NOT NULL,
    text         TEXT    NOT NULL,
    independent  BOOLEAN NOT NULL,
    depends_on   INTEGER,
    PRIMARY KEY (patent_id, claim_number)
);

CREATE TABLE patent_inventors (
    patent_id BIGINT       NOT NULL REFERENCES patents (id) ON DELETE CASCADE,
    seq       INTEGER      NOT NULL,
    name      VARCHAR(300) NOT NULL,
    city      VARCHAR(120),
    state     VARCHAR(60),
    country   VARCHAR(2),
    PRIMARY KEY (patent_id, seq)
);

CREATE INDEX ix_patent_inventors_name_trgm ON patent_inventors USING GIN (lower(name) public.gin_trgm_ops);

CREATE TABLE patent_assignees (
    patent_id BIGINT       NOT NULL REFERENCES patents (id) ON DELETE CASCADE,
    seq       INTEGER      NOT NULL,
    name      VARCHAR(300) NOT NULL,
    city      VARCHAR(120),
    state     VARCHAR(60),
    country   VARCHAR(2),
    PRIMARY KEY (patent_id, seq)
);

CREATE INDEX ix_patent_assignees_name_trgm ON patent_assignees USING GIN (lower(name) public.gin_trgm_ops);
CREATE INDEX ix_patent_assignees_name ON patent_assignees (name);

CREATE TABLE patent_cpc (
    patent_id BIGINT      NOT NULL REFERENCES patents (id) ON DELETE CASCADE,
    seq       INTEGER     NOT NULL,
    code      VARCHAR(32) NOT NULL,
    PRIMARY KEY (patent_id, seq)
);

CREATE INDEX ix_patent_cpc_code ON patent_cpc (code varchar_pattern_ops);

CREATE TABLE patent_citations (
    patent_id            BIGINT      NOT NULL REFERENCES patents (id) ON DELETE CASCADE,
    seq                  INTEGER     NOT NULL,
    cited_patent_number  VARCHAR(32) NOT NULL,
    cited_by             VARCHAR(16) NOT NULL,
    PRIMARY KEY (patent_id, seq),
    CONSTRAINT ck_patent_citations_by CHECK (cited_by IN ('EXAMINER', 'APPLICANT'))
);

CREATE INDEX ix_patent_citations_cited ON patent_citations (cited_patent_number);
