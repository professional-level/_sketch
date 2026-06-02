-- Migration: add retry scheduling columns to Kafka outbox tables.
-- Target database: MySQL 8 compatible schemas used by strategy-execution-service
-- and stock-purchase-service.
--
-- Apply this before deploying service versions that delay failed outbox retries
-- with nextAttemptAt. The project currently uses Hibernate ddl-auto=update for
-- local sketch runs, but production should apply this DDL explicitly.
--
-- Preflight check:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN ('order_intent_outbox_event', 'order_execution_outbox_event')
--   AND COLUMN_NAME = 'nextAttemptAt'
-- ORDER BY TABLE_NAME, COLUMN_NAME;
--
-- If any target column already exists, stop and reconcile the schema before
-- applying the ALTER statements below.

ALTER TABLE order_intent_outbox_event
    ADD COLUMN nextAttemptAt DATETIME(6) NULL AFTER failureReason;

ALTER TABLE order_execution_outbox_event
    ADD COLUMN nextAttemptAt DATETIME(6) NULL AFTER failureReason;

-- Post-apply verification:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN ('order_intent_outbox_event', 'order_execution_outbox_event')
--   AND COLUMN_NAME = 'nextAttemptAt'
-- ORDER BY TABLE_NAME, COLUMN_NAME;
--
-- Expected result: two nullable DATETIME columns.
--
-- Rollback, if needed before any dependent deployment relies on these fields:
-- ALTER TABLE order_intent_outbox_event
--     DROP COLUMN nextAttemptAt;
-- ALTER TABLE order_execution_outbox_event
--     DROP COLUMN nextAttemptAt;
