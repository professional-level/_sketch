-- Migration: create stock-purchase-service reconciliation state tables.
-- Target database: MySQL 8 compatible schema used by stock-purchase-service.
--
-- Apply this before deploying service versions that persist broker fills,
-- reconciliation cursors, and unmatched broker executions. The project
-- currently uses Hibernate ddl-auto=update for local sketch runs, but
-- production should apply this DDL explicitly.
--
-- Preflight check:
-- SELECT TABLE_NAME
-- FROM INFORMATION_SCHEMA.TABLES
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN (
--       'execution_fill',
--       'execution_reconciliation_cursor',
--       'unmatched_execution'
--   )
-- ORDER BY TABLE_NAME;
--
-- If any target table already exists, stop and reconcile the schema before
-- applying this migration.

CREATE TABLE IF NOT EXISTS execution_fill (
    externalExecutionId VARCHAR(255) NOT NULL,
    externalOrderId VARCHAR(255) NOT NULL,
    stockId VARCHAR(255) NOT NULL,
    stockName VARCHAR(255) NOT NULL,
    createdAt DATETIME(6) NOT NULL,
    quantity INT NOT NULL,
    type VARCHAR(32) NOT NULL,
    PRIMARY KEY (externalExecutionId),
    KEY idx_execution_fill_order (externalOrderId),
    KEY idx_execution_fill_created_at (createdAt)
);

CREATE TABLE IF NOT EXISTS execution_reconciliation_cursor (
    source VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attemptCount INT NOT NULL,
    lastStartedAt DATETIME(6) NULL,
    lastCompletedAt DATETIME(6) NULL,
    lastFailedAt DATETIME(6) NULL,
    lastObservedExecutionId VARCHAR(255) NULL,
    lastObservedExecutionAt DATETIME(6) NULL,
    observedExecutionCount INT NOT NULL,
    savedFillCount INT NOT NULL,
    unmatchedExecutionCount INT NOT NULL,
    failureReason VARCHAR(1000) NULL,
    PRIMARY KEY (source),
    KEY idx_execution_reconciliation_status (status),
    KEY idx_execution_reconciliation_last_observed_at (lastObservedExecutionAt)
);

CREATE TABLE IF NOT EXISTS unmatched_execution (
    externalExecutionId VARCHAR(255) NOT NULL,
    externalOrderId VARCHAR(255) NOT NULL,
    stockId VARCHAR(255) NOT NULL,
    stockName VARCHAR(255) NOT NULL,
    createdAt DATETIME(6) NOT NULL,
    quantity INT NOT NULL,
    type VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    observedAt DATETIME(6) NOT NULL,
    PRIMARY KEY (externalExecutionId),
    KEY idx_unmatched_execution_order (externalOrderId),
    KEY idx_unmatched_execution_observed_at (observedAt),
    KEY idx_unmatched_execution_reason (reason)
);

-- Post-apply verification:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN (
--       'execution_fill',
--       'execution_reconciliation_cursor',
--       'unmatched_execution'
--   )
-- ORDER BY TABLE_NAME, ORDINAL_POSITION;
--
-- Expected result: primary keys on execution id/source, non-null broker fill
-- fields, nullable cursor timestamps and failure reason, and unmatched
-- execution indexes for operations lookup.
--
-- Rollback, if needed before deploying dependent code:
-- DROP TABLE unmatched_execution;
-- DROP TABLE execution_reconciliation_cursor;
-- DROP TABLE execution_fill;
