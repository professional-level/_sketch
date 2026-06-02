-- Migration: persist order-intent submission overseas exchange code.
-- Target database: MySQL 8 compatible schema used by stock-purchase-service.
--
-- Apply this before deploying service versions that route overseas KIS order
-- submit, cancel, and status lookup requests by the submitted exchange code.
-- The project currently uses Hibernate ddl-auto=update for local sketch runs,
-- but production should apply this DDL explicitly.
--
-- Preflight check:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'order_intent_submission'
--   AND COLUMN_NAME = 'exchange'
-- ORDER BY TABLE_NAME, COLUMN_NAME;
--
-- If the target column already exists, stop and reconcile the schema before
-- applying the ALTER statement below.

ALTER TABLE order_intent_submission
    ADD COLUMN exchange VARCHAR(16) NULL AFTER symbol;

-- Existing rows are left NULL because older submissions did not persist an
-- overseas exchange. The service reads NULL or blank values as NASD, preserving
-- the previous behavior while allowing new rows to store NYSE/AMEX/NASD.

-- Post-apply verification:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'order_intent_submission'
--   AND COLUMN_NAME = 'exchange'
-- ORDER BY TABLE_NAME, COLUMN_NAME;
--
-- Expected result: one nullable VARCHAR(16) exchange column.
--
-- Rollback, if needed before any dependent deployment relies on this field:
-- ALTER TABLE order_intent_submission
--     DROP COLUMN exchange;
