-- Migration: create stock-purchase-service legacy order state tables.
-- Target database: MySQL 8 compatible schema used by stock-purchase-service.
--
-- Apply this when bootstrapping a production-like schema from scratch for the
-- legacy stock_order flow that still backs scheduler-created sell orders and
-- processed event idempotency. The project currently uses Hibernate
-- ddl-auto=update for local sketch runs, but production should apply this DDL
-- explicitly.
--
-- Preflight check:
-- SELECT TABLE_NAME
-- FROM INFORMATION_SCHEMA.TABLES
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN (
--       'stock_order',
--       'order_id_mapping',
--       'processed_event'
--   )
-- ORDER BY TABLE_NAME;
--
-- If any target table already exists, stop and reconcile the schema before
-- applying this migration.

CREATE TABLE IF NOT EXISTS stock_order (
    id BINARY(16) NOT NULL,
    strategyId VARCHAR(255) NULL,
    stockId VARCHAR(255) NOT NULL,
    stockName VARCHAR(255) NOT NULL,
    requestedAt DATETIME(6) NOT NULL,
    strategyType VARCHAR(255) NOT NULL,
    purchasedAt DATETIME(6) NULL,
    sellingAt DATETIME(6) NULL,
    purchasePrice DOUBLE NULL,
    sellingPrice DOUBLE NULL,
    quantity INT NOT NULL,
    orderState VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_order_strategy_id (strategyId),
    KEY idx_stock_order_state (orderState),
    KEY idx_stock_order_not_completed (purchasedAt, sellingAt),
    KEY idx_stock_order_stock_quantity (stockId, quantity, requestedAt)
);

CREATE TABLE IF NOT EXISTS order_id_mapping (
    externalOrderId VARCHAR(255) NOT NULL,
    internalOrderId BINARY(16) NOT NULL,
    type VARCHAR(255) NULL,
    PRIMARY KEY (externalOrderId),
    KEY idx_order_id_mapping_internal_order (internalOrderId)
);

CREATE TABLE IF NOT EXISTS processed_event (
    eventId BINARY(16) NOT NULL,
    idempotencyKey VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    startedAt DATETIME(6) NOT NULL,
    completedAt DATETIME(6) NULL,
    failureReason VARCHAR(255) NULL,
    PRIMARY KEY (eventId),
    UNIQUE KEY uk_processed_event_idempotency_key (idempotencyKey),
    KEY idx_processed_event_status_started (status, startedAt)
);

-- Post-apply verification:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN (
--       'stock_order',
--       'order_id_mapping',
--       'processed_event'
--   )
-- ORDER BY TABLE_NAME, ORDINAL_POSITION;
--
-- Expected result: stock_order has the legacy order lifecycle fields and
-- strategyId uniqueness, order_id_mapping links broker ids to internal order
-- ids, and processed_event stores event idempotency state.
--
-- Rollback, if needed before deploying dependent code:
-- DROP TABLE processed_event;
-- DROP TABLE order_id_mapping;
-- DROP TABLE stock_order;
