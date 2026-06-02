-- Migration: create final-price single-shot strategy execution state table.
-- Target database: MySQL 8 compatible schema used by strategy-execution-service.
--
-- Apply this before deploying service versions that persist FinalPriceBatingV1
-- lifecycle state. The project currently uses Hibernate ddl-auto=update for
-- local sketch runs, but production should apply this DDL explicitly.
--
-- Preflight check:
-- SELECT TABLE_NAME
-- FROM INFORMATION_SCHEMA.TABLES
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'final_price_bating_v1_strategy_execution';
--
-- If the table already exists, stop and reconcile the schema before applying
-- this migration.

CREATE TABLE IF NOT EXISTS final_price_bating_v1_strategy_execution (
    executionId VARCHAR(255) NOT NULL,
    symbol VARCHAR(255) NOT NULL,
    market VARCHAR(255) NOT NULL,
    budget DOUBLE NOT NULL,
    targetBuyPrice DOUBLE NOT NULL,
    quantity BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    filledQuantity BIGINT NOT NULL,
    averageFilledPrice DOUBLE NULL,
    startedAt DATETIME(6) NOT NULL,
    completedAt DATETIME(6) NULL,
    PRIMARY KEY (executionId),
    KEY idx_final_price_bating_v1_status (status),
    KEY idx_final_price_bating_v1_symbol_market (symbol, market)
);

-- Post-apply verification:
-- SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'final_price_bating_v1_strategy_execution'
-- ORDER BY ORDINAL_POSITION;
--
-- Expected result: executionId primary key, non-null strategy/order state
-- columns, nullable averageFilledPrice and completedAt.
--
-- Rollback, if needed before deploying dependent code:
-- DROP TABLE final_price_bating_v1_strategy_execution;
