-- Identity: users, roles, API keys, usage.
-- pg_trgm is a trusted extension (PG13+), so the database owner can create it without superuser.
-- It is installed in "public" so every schema can use its operator classes.
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

CREATE TABLE roles (
    name        VARCHAR(32) PRIMARY KEY,
    description VARCHAR(200) NOT NULL
);

INSERT INTO roles (name, description) VALUES
    ('USER',  'Registered portal user'),
    ('ADMIN', 'Portal administrator');

CREATE TABLE users (
    id                 UUID         PRIMARY KEY,
    email              VARCHAR(254) NOT NULL,
    password_hash      VARCHAR(200) NOT NULL,
    display_name       VARCHAR(100) NOT NULL,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_count INTEGER      NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    last_login_at      TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email))
);

CREATE INDEX ix_users_created_at ON users (created_at DESC);

CREATE TABLE user_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(32) NOT NULL REFERENCES roles (name),
    PRIMARY KEY (user_id, role)
);

CREATE TABLE api_keys (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name          VARCHAR(100) NOT NULL,
    key_hash      VARCHAR(64)     NOT NULL,
    prefix        VARCHAR(16)  NOT NULL,
    tier          VARCHAR(16)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    rotated_at    TIMESTAMPTZ,
    last_used_at  TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,
    request_count BIGINT       NOT NULL DEFAULT 0,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_api_keys_hash UNIQUE (key_hash),
    CONSTRAINT ck_api_keys_tier CHECK (tier IN ('FREE', 'WEB', 'ADMIN'))
);

CREATE INDEX ix_api_keys_user ON api_keys (user_id, created_at DESC);
CREATE INDEX ix_api_keys_user_active ON api_keys (user_id) WHERE revoked_at IS NULL;

CREATE TABLE api_usage_daily (
    key_id        UUID    NOT NULL REFERENCES api_keys (id) ON DELETE CASCADE,
    usage_date    DATE    NOT NULL,
    request_count BIGINT  NOT NULL DEFAULT 0,
    CONSTRAINT pk_api_usage_daily PRIMARY KEY (key_id, usage_date),
    CONSTRAINT ck_api_usage_count CHECK (request_count >= 0)
);

CREATE INDEX ix_api_usage_daily_date ON api_usage_daily (usage_date);
