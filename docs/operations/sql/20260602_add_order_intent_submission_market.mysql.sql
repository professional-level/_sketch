-- Migration: persist order-intent submission market for currency-aware risk checks.
-- Target database: MySQL 8 compatible schema used by stock-purchase-service.
--
-- Apply this before deploying service versions that normalize account risk
-- amounts across multiple currencies. The project currently uses Hibernate
-- ddl-auto=update for local sketch runs, but production should apply this DDL
-- explicitly.
--
-- Preflight check:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'order_intent_submission'
--   AND COLUMN_NAME = 'market'
-- ORDER BY TABLE_NAME, COLUMN_NAME;
--
-- If the target column already exists, stop and reconcile the schema before
-- applying the ALTER statement below.

ALTER TABLE order_intent_submission
    ADD COLUMN market VARCHAR(32) NULL AFTER symbol;

-- Existing rows are left NULL because older submissions did not persist market.
-- The service treats NULL active buy rows as belonging to the current market
-- during risk checks so legacy rows remain conservatively counted until they
-- age out of active statuses.

-- Post-apply verification:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'order_intent_submission'
--   AND COLUMN_NAME = 'market'
-- ORDER BY TABLE_NAME, COLUMN_NAME;
--
-- Expected result: one nullable VARCHAR(32) market column.
--
-- Rollback, if needed before any dependent deployment relies on this field:
-- ALTER TABLE order_intent_submission
--     DROP COLUMN market;
