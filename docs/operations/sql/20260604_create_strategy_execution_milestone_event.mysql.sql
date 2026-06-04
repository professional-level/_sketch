-- Migration: create strategy-execution-service Flink milestone idempotency table.
-- Target database: MySQL 8 compatible schema used by strategy-execution-service.
--
-- Apply this before enabling the stream-processing-service LAOR order lifecycle
-- Flink job in production-like environments. The table records Flink milestone
-- events before they are bridged into Temporal workflow signals.
--
-- Preflight check:
-- SELECT TABLE_NAME
-- FROM INFORMATION_SCHEMA.TABLES
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'strategy_execution_milestone_event';
--
-- If the table already exists, stop and reconcile the schema before applying
-- this migration.

CREATE TABLE IF NOT EXISTS strategy_execution_milestone_event (
    eventId VARCHAR(255) NOT NULL,
    milestoneType VARCHAR(255) NOT NULL,
    strategyExecutionId VARCHAR(255) NOT NULL,
    orderIntentId VARCHAR(255) NOT NULL,
    brokerOrderId VARCHAR(255) NULL,
    orderTag VARCHAR(255) NULL,
    side VARCHAR(255) NULL,
    filledQuantity BIGINT NOT NULL,
    averageFilledPrice DOUBLE NULL,
    occurredAt DATETIME(6) NOT NULL,
    sourceEventIds TEXT NOT NULL,
    idempotencyKey VARCHAR(512) NOT NULL,
    PRIMARY KEY (eventId),
    UNIQUE KEY uk_strategy_milestone_idempotency (idempotencyKey),
    KEY idx_strategy_milestone_execution_time (strategyExecutionId, occurredAt),
    KEY idx_strategy_milestone_order_intent (orderIntentId),
    KEY idx_strategy_milestone_type_time (milestoneType, occurredAt)
);

-- Post-apply verification:
-- SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'strategy_execution_milestone_event'
-- ORDER BY ORDINAL_POSITION;
--
-- Expected result: Flink milestone audit/idempotency columns are present and
-- idempotencyKey has a unique key.
--
-- Rollback, if needed before deploying dependent code:
-- DROP TABLE strategy_execution_milestone_event;
