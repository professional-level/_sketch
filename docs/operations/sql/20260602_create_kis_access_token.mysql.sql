-- Migration: create a shared KIS access token store for the root KIS wrapper.
-- Target database: MySQL 8 compatible schema.
--
-- Apply this before enabling:
--
-- akra.kis.token.persistence.enabled=true
-- akra.kis.token.persistence.type=jdbc
--
-- The table stores one token per KIS token scope. Current scopes are REAL and
-- MOCK. Store application credentials separately; this table stores issued
-- access tokens only.

CREATE TABLE IF NOT EXISTS kis_access_token (
    token_scope VARCHAR(16) NOT NULL,
    access_token TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (token_scope)
);

-- Verification:
-- SELECT token_scope, expires_at, updated_at
-- FROM kis_access_token
-- ORDER BY token_scope;
--
-- Rollback, if needed before enabling JDBC persistence:
-- DROP TABLE kis_access_token;
