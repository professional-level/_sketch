-- Migration: create stock-search-service durable discovery and outbox tables.
-- Target database: MySQL 8 compatible schema used by stock-search-service.
--
-- Apply this before running stock-search-service with a production-like
-- profile and spring.jpa.hibernate.ddl-auto=validate or none. The project
-- currently uses Hibernate ddl-auto=update for local sketch runs, but
-- production should apply this DDL explicitly.
--
-- Preflight check:
-- SELECT TABLE_NAME
-- FROM INFORMATION_SCHEMA.TABLES
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN (
--       'stock_volume_rank',
--       'stock_suggestion',
--       'outbox_event'
--   )
-- ORDER BY TABLE_NAME;
--
-- If any target table already exists, stop and reconcile the schema before
-- applying this migration.

CREATE TABLE IF NOT EXISTS stock_volume_rank (
    sequence BIGINT NOT NULL AUTO_INCREMENT,
    stockId VARCHAR(255) NOT NULL,
    price INT NOT NULL,
    derivative DOUBLE NOT NULL,
    volume BIGINT NULL,
    dateTime DATETIME(6) NULL,
    infoType VARCHAR(255) NULL,
    rank TINYINT UNSIGNED NOT NULL,
    PRIMARY KEY (sequence),
    KEY idx_stock_volume_rank_stock_time (stockId, dateTime),
    KEY idx_stock_volume_rank_info_time (infoType, dateTime)
);

CREATE TABLE IF NOT EXISTS stock_suggestion (
    sequence BIGINT NOT NULL AUTO_INCREMENT,
    id VARCHAR(255) NOT NULL,
    dateTime DATETIME(6) NOT NULL,
    strategyType VARCHAR(255) NULL,
    PRIMARY KEY (sequence),
    KEY idx_stock_suggestion_strategy_time (strategyType, dateTime),
    KEY idx_stock_suggestion_stock_time (id, dateTime)
);

CREATE TABLE IF NOT EXISTS outbox_event (
    id BINARY(16) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    eventType VARCHAR(255) NOT NULL,
    payload LONGBLOB NOT NULL,
    status VARCHAR(255) NOT NULL,
    retryCount INT NOT NULL,
    createdAt DATETIME(6) NOT NULL,
    publishedAt DATETIME(6) NULL,
    failureReason VARCHAR(255) NULL,
    PRIMARY KEY (id),
    KEY idx_stock_search_outbox_status_created (status, createdAt)
);

-- Post-apply verification:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN (
--       'stock_volume_rank',
--       'stock_suggestion',
--       'outbox_event'
--   )
-- ORDER BY TABLE_NAME, ORDINAL_POSITION;
--
-- Expected result: stock_volume_rank and stock_suggestion have
-- AUTO_INCREMENT sequence primary keys and query indexes for operational
-- discovery history. outbox_event has the base Kafka payload/status columns
-- needed for restart-safe strategy start request publishing.
--
-- Rollback, if needed before deploying dependent code:
-- DROP TABLE outbox_event;
-- DROP TABLE stock_suggestion;
-- DROP TABLE stock_volume_rank;
