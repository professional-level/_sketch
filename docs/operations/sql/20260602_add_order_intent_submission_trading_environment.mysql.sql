-- Migration: persist expected order-intent trading environment.
-- Target database: MySQL 8 compatible schema used by stock-purchase-service.
--
-- Apply this before deploying service versions that validate and audit
-- OrderIntentCreated.trading_environment against the actual broker mock/live
-- route. The project currently uses Hibernate ddl-auto=update for local sketch
-- runs, but production should apply this DDL explicitly.
--
-- Preflight check:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'order_intent_submission'
--   AND COLUMN_NAME = 'tradingEnvironment'
-- ORDER BY TABLE_NAME, COLUMN_NAME;
--
-- If the target column already exists, stop and reconcile the schema before
-- applying the ALTER statement below.

ALTER TABLE order_intent_submission
    ADD COLUMN tradingEnvironment VARCHAR(16) NULL AFTER submittedAt;

-- Post-apply verification:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'order_intent_submission'
--   AND COLUMN_NAME = 'tradingEnvironment'
-- ORDER BY TABLE_NAME, COLUMN_NAME;
--
-- Expected result: one nullable VARCHAR(16) tradingEnvironment column.
--
-- Rollback, if needed before any dependent deployment relies on this field:
-- ALTER TABLE order_intent_submission
--     DROP COLUMN tradingEnvironment;
