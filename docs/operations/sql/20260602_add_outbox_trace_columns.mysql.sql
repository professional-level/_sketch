-- Migration: add trace context columns to Kafka outbox tables.
-- Target database: MySQL 8 compatible schemas used by strategy-execution-service
-- and stock-purchase-service.
--
-- Apply this before deploying service versions that read/write outbox trace
-- fields. The project currently uses Hibernate ddl-auto=update for local sketch
-- runs, but production should apply this DDL explicitly.
--
-- Preflight check:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN ('order_intent_outbox_event', 'order_execution_outbox_event')
--   AND COLUMN_NAME IN ('traceId', 'spanId', 'traceParent')
-- ORDER BY TABLE_NAME, COLUMN_NAME;
--
-- If any target column already exists, stop and reconcile the schema before
-- applying the ALTER statements below.

ALTER TABLE order_intent_outbox_event
    ADD COLUMN traceId VARCHAR(255) NULL AFTER payload,
    ADD COLUMN spanId VARCHAR(255) NULL AFTER traceId,
    ADD COLUMN traceParent VARCHAR(255) NULL AFTER spanId;

ALTER TABLE order_execution_outbox_event
    ADD COLUMN traceId VARCHAR(255) NULL AFTER payload,
    ADD COLUMN spanId VARCHAR(255) NULL AFTER traceId,
    ADD COLUMN traceParent VARCHAR(255) NULL AFTER spanId;

-- Post-apply verification:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN ('order_intent_outbox_event', 'order_execution_outbox_event')
--   AND COLUMN_NAME IN ('traceId', 'spanId', 'traceParent')
-- ORDER BY TABLE_NAME, COLUMN_NAME;
--
-- Expected result: six nullable VARCHAR(255) columns.
--
-- Rollback, if needed before any dependent deployment relies on these fields:
-- ALTER TABLE order_intent_outbox_event
--     DROP COLUMN traceParent,
--     DROP COLUMN spanId,
--     DROP COLUMN traceId;
-- ALTER TABLE order_execution_outbox_event
--     DROP COLUMN traceParent,
--     DROP COLUMN spanId,
--     DROP COLUMN traceId;
