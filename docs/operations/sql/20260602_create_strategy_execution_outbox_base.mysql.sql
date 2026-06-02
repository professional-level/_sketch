-- Migration: create strategy-execution-service order-intent outbox base table.
-- Target database: MySQL 8 compatible schema used by strategy-execution-service.
--
-- Apply this before the outbox trace, retry scheduling, and claim lease ALTER
-- migrations when bootstrapping a production-like schema from scratch. The
-- project currently uses Hibernate ddl-auto=update for local sketch runs, but
-- production should apply this DDL explicitly.
--
-- Preflight check:
-- SELECT TABLE_NAME
-- FROM INFORMATION_SCHEMA.TABLES
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'order_intent_outbox_event';
--
-- If the table already exists, stop and reconcile the schema before applying
-- this migration.

CREATE TABLE IF NOT EXISTS order_intent_outbox_event (
    id BINARY(16) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    messageKey VARCHAR(255) NOT NULL,
    eventType VARCHAR(255) NOT NULL,
    payload LONGBLOB NOT NULL,
    status VARCHAR(255) NOT NULL,
    retryCount INT NOT NULL,
    createdAt DATETIME(6) NOT NULL,
    publishedAt DATETIME(6) NULL,
    failureReason VARCHAR(255) NULL,
    PRIMARY KEY (id),
    KEY idx_order_intent_outbox_status_created (status, createdAt)
);

-- Post-apply verification:
-- SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'order_intent_outbox_event'
-- ORDER BY ORDINAL_POSITION;
--
-- Expected result: primary key id, non-null Kafka topic/key/type/payload,
-- status/retry/created fields, nullable publishedAt and failureReason, and no
-- traceId/spanId/traceParent/nextAttemptAt/claimOwner/claimExpiresAt columns
-- until the follow-up ALTER migrations are applied.
--
-- Rollback, if needed before deploying dependent code:
-- DROP TABLE order_intent_outbox_event;
