-- Migration: create strategy-execution-service durable state tables.
-- Target database: MySQL 8 compatible schema used by strategy-execution-service.
--
-- Apply this when bootstrapping a production-like schema from scratch for
-- strategy start idempotency, order event recording, and Laor V4 state. The
-- project currently uses Hibernate ddl-auto=update for local sketch runs, but
-- production should apply this DDL explicitly.
--
-- Preflight check:
-- SELECT TABLE_NAME
-- FROM INFORMATION_SCHEMA.TABLES
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN (
--       'strategy_execution_start_request',
--       'strategy_execution_order_event',
--       'laor_v4_strategy_execution'
--   )
-- ORDER BY TABLE_NAME;
--
-- If any target table already exists, stop and reconcile the schema before
-- applying this migration.

CREATE TABLE IF NOT EXISTS strategy_execution_start_request (
    idempotencyKey VARCHAR(255) NOT NULL,
    processedAt DATETIME(6) NOT NULL,
    PRIMARY KEY (idempotencyKey),
    KEY idx_strategy_start_request_processed_at (processedAt)
);

CREATE TABLE IF NOT EXISTS strategy_execution_order_event (
    eventId VARCHAR(255) NOT NULL,
    strategyExecutionId VARCHAR(255) NOT NULL,
    orderIntentId VARCHAR(255) NOT NULL,
    brokerOrderId VARCHAR(255) NULL,
    type VARCHAR(255) NOT NULL,
    side VARCHAR(255) NULL,
    price DOUBLE NULL,
    quantity BIGINT NULL,
    orderTag VARCHAR(255) NULL,
    reason VARCHAR(255) NULL,
    occurredAt DATETIME(6) NOT NULL,
    PRIMARY KEY (eventId),
    KEY idx_strategy_order_event_execution_time (strategyExecutionId, occurredAt),
    KEY idx_strategy_order_event_broker_order (brokerOrderId),
    KEY idx_strategy_order_event_type_time (type, occurredAt)
);

CREATE TABLE IF NOT EXISTS laor_v4_strategy_execution (
    executionId VARCHAR(255) NOT NULL,
    symbol VARCHAR(255) NOT NULL,
    totalSplitCount INT NOT NULL,
    firstBuyLimitMultiplier DOUBLE NOT NULL,
    autoRestart BIT(1) NOT NULL,
    cycleNo INT NOT NULL,
    status VARCHAR(255) NOT NULL,
    mode VARCHAR(255) NOT NULL,
    progressRound DOUBLE NOT NULL,
    availableCash DOUBLE NOT NULL,
    holdingQuantity BIGINT NOT NULL,
    averagePurchasePrice DOUBLE NOT NULL,
    realizedProfitLoss DOUBLE NOT NULL,
    reverseModeElapsedDays INT NOT NULL,
    lastExecutionRunId VARCHAR(255) NULL,
    lastExecutedAt DATETIME(6) NULL,
    PRIMARY KEY (executionId),
    KEY idx_laor_v4_status (status),
    KEY idx_laor_v4_symbol_status (symbol, status)
);

-- Post-apply verification:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN (
--       'strategy_execution_start_request',
--       'strategy_execution_order_event',
--       'laor_v4_strategy_execution'
--   )
-- ORDER BY TABLE_NAME, ORDINAL_POSITION;
--
-- Expected result: start request idempotency, order event audit records, and
-- Laor V4 lifecycle/state columns are present with primary keys and operations
-- indexes.
--
-- Rollback, if needed before deploying dependent code:
-- DROP TABLE laor_v4_strategy_execution;
-- DROP TABLE strategy_execution_order_event;
-- DROP TABLE strategy_execution_start_request;
