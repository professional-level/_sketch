-- KIS token refresh lock table for the root broker wrapper.
--
-- Preflight:
-- SELECT table_name
-- FROM information_schema.tables
-- WHERE table_schema = DATABASE()
--   AND table_name = 'kis_token_refresh_lock';

CREATE TABLE IF NOT EXISTS kis_token_refresh_lock (
    token_scope VARCHAR(16) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    locked_until TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (token_scope)
);

-- Post-apply verification:
-- SELECT column_name, data_type, is_nullable
-- FROM information_schema.columns
-- WHERE table_schema = DATABASE()
--   AND table_name = 'kis_token_refresh_lock'
-- ORDER BY ordinal_position;

-- Rollback:
-- DROP TABLE kis_token_refresh_lock;
