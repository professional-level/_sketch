-- Migration: add claim lease columns to Kafka outbox tables.
-- Target database: MySQL 8 compatible schemas used by strategy-execution-service
-- and stock-purchase-service.
--
-- Apply this before deploying service versions that claim outbox rows with
-- PROCESSING status. The project currently uses Hibernate ddl-auto=update for
-- local sketch runs, but production should apply this DDL explicitly.
--
-- Preflight check:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN ('order_intent_outbox_event', 'order_execution_outbox_event')
--   AND COLUMN_NAME IN ('claimOwner', 'claimExpiresAt')
-- ORDER BY TABLE_NAME, COLUMN_NAME;
--
-- If any target column already exists, stop and reconcile the schema before
-- applying the ALTER statements below.

ALTER TABLE order_intent_outbox_event
    ADD COLUMN claimOwner VARCHAR(255) NULL AFTER nextAttemptAt,
    ADD COLUMN claimExpiresAt DATETIME(6) NULL AFTER claimOwner;

ALTER TABLE order_execution_outbox_event
    ADD COLUMN claimOwner VARCHAR(255) NULL AFTER nextAttemptAt,
    ADD COLUMN claimExpiresAt DATETIME(6) NULL AFTER claimOwner;

-- Post-apply verification:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN ('order_intent_outbox_event', 'order_execution_outbox_event')
--   AND COLUMN_NAME IN ('claimOwner', 'claimExpiresAt')
-- ORDER BY TABLE_NAME, COLUMN_NAME;
--
-- Expected result: two nullable VARCHAR(255) claimOwner columns and two
-- nullable DATETIME(6) claimExpiresAt columns.
--
-- Rollback, if needed before any dependent deployment relies on these fields:
-- ALTER TABLE order_intent_outbox_event
--     DROP COLUMN claimExpiresAt,
--     DROP COLUMN claimOwner;
-- ALTER TABLE order_execution_outbox_event
--     DROP COLUMN claimExpiresAt,
--     DROP COLUMN claimOwner;
