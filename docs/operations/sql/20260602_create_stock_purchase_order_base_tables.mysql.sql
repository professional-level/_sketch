-- Migration: create stock-purchase-service order submission and outbox base tables.
-- Target database: MySQL 8 compatible schema used by stock-purchase-service.
--
-- Apply this before the order-intent submission and outbox ALTER migrations
-- when bootstrapping a production-like schema from scratch. The project
-- currently uses Hibernate ddl-auto=update for local sketch runs, but
-- production should apply this DDL explicitly.
--
-- Preflight check:
-- SELECT TABLE_NAME
-- FROM INFORMATION_SCHEMA.TABLES
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN (
--       'order_intent_submission',
--       'order_execution_outbox_event'
--   )
-- ORDER BY TABLE_NAME;
--
-- If any target table already exists, stop and reconcile the schema before
-- applying this migration.

CREATE TABLE IF NOT EXISTS order_intent_submission (
    orderIntentId BINARY(16) NOT NULL,
    idempotencyKey VARCHAR(255) NOT NULL,
    strategyExecutionId VARCHAR(255) NOT NULL,
    symbol VARCHAR(255) NOT NULL,
    side VARCHAR(255) NOT NULL,
    orderType VARCHAR(255) NOT NULL,
    submittedPrice DOUBLE NULL,
    quantity BIGINT NOT NULL,
    orderTag VARCHAR(255) NOT NULL,
    internalOrderId BINARY(16) NOT NULL,
    externalOrderId VARCHAR(255) NULL,
    submittedAt DATETIME(6) NOT NULL,
    status VARCHAR(255) NOT NULL,
    statusReason VARCHAR(1000) NULL,
    lastStatusCheckedAt DATETIME(6) NULL,
    PRIMARY KEY (orderIntentId),
    UNIQUE KEY uk_order_intent_submission_idempotency (idempotencyKey),
    UNIQUE KEY uk_order_intent_submission_external_order (externalOrderId),
    KEY idx_order_intent_submission_status_submitted (status, submittedAt),
    KEY idx_order_intent_submission_strategy_window (strategyExecutionId, submittedAt, status)
);

CREATE TABLE IF NOT EXISTS order_execution_outbox_event (
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
    KEY idx_order_execution_outbox_status_created (status, createdAt)
);

-- Post-apply verification:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN (
--       'order_intent_submission',
--       'order_execution_outbox_event'
--   )
-- ORDER BY TABLE_NAME, ORDINAL_POSITION;
--
-- Expected result: order_intent_submission has idempotency/external order
-- uniqueness, broker submission state columns, and no branchOrderNumber,
-- exchange, tradingEnvironment, or market columns until the follow-up ALTER
-- migrations are applied. order_execution_outbox_event has the base Kafka
-- outbox columns and no traceId/spanId/traceParent/nextAttemptAt/claimOwner/
-- claimExpiresAt columns until the follow-up ALTER migrations are applied.
--
-- Rollback, if needed before deploying dependent code:
-- DROP TABLE order_execution_outbox_event;
-- DROP TABLE order_intent_submission;
