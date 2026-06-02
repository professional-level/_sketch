-- Migration: add final-price sell lifecycle columns.
-- Target database: MySQL 8 compatible schema used by strategy-execution-service.
--
-- Apply this after creating final_price_bating_v1_strategy_execution and
-- before deploying service versions that persist sell intent and sell fill
-- completion state for the final-price bating strategy.
--
-- Preflight check:
-- SELECT COLUMN_NAME
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'final_price_bating_v1_strategy_execution'
--   AND COLUMN_NAME IN (
--       'sellTargetPrice',
--       'sellQuantity',
--       'sellIntentCreatedAt',
--       'soldQuantity',
--       'averageSoldPrice'
--   )
-- ORDER BY COLUMN_NAME;
--
-- If any target column already exists, stop and reconcile the schema before
-- applying the ALTER statement below.

ALTER TABLE final_price_bating_v1_strategy_execution
    ADD COLUMN sellTargetPrice DOUBLE NULL AFTER averageFilledPrice,
    ADD COLUMN sellQuantity BIGINT NOT NULL DEFAULT 0 AFTER sellTargetPrice,
    ADD COLUMN sellIntentCreatedAt DATETIME(6) NULL AFTER sellQuantity,
    ADD COLUMN soldQuantity BIGINT NOT NULL DEFAULT 0 AFTER sellIntentCreatedAt,
    ADD COLUMN averageSoldPrice DOUBLE NULL AFTER soldQuantity;

-- Post-apply verification:
-- SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'final_price_bating_v1_strategy_execution'
--   AND COLUMN_NAME IN (
--       'sellTargetPrice',
--       'sellQuantity',
--       'sellIntentCreatedAt',
--       'soldQuantity',
--       'averageSoldPrice'
--   )
-- ORDER BY ORDINAL_POSITION;
--
-- Expected result: nullable sellTargetPrice, sellIntentCreatedAt, and
-- averageSoldPrice columns plus non-null sellQuantity and soldQuantity columns
-- defaulted to 0 for existing active rows.
--
-- Rollback, if needed before deploying dependent code:
-- ALTER TABLE final_price_bating_v1_strategy_execution
--     DROP COLUMN averageSoldPrice,
--     DROP COLUMN soldQuantity,
--     DROP COLUMN sellIntentCreatedAt,
--     DROP COLUMN sellQuantity,
--     DROP COLUMN sellTargetPrice;
