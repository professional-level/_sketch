-- Migration: persist KIS branch/order-org numbers for submitted order intents.
-- Target database: MySQL 8 compatible schema used by stock-purchase-service.
--
-- Apply this before deploying service versions that reuse KIS domestic
-- KRX_FWDG_ORD_ORGNO values for later cancel/recovery requests. The project
-- currently uses Hibernate ddl-auto=update for local sketch runs, but
-- production should apply this DDL explicitly.
--
-- Preflight check:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'order_intent_submission'
--   AND COLUMN_NAME = 'branchOrderNumber'
-- ORDER BY TABLE_NAME, COLUMN_NAME;
--
-- If the target column already exists, stop and reconcile the schema before
-- applying the ALTER statement below.

ALTER TABLE order_intent_submission
    ADD COLUMN branchOrderNumber VARCHAR(255) NULL AFTER externalOrderId;

-- Post-apply verification:
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'order_intent_submission'
--   AND COLUMN_NAME = 'branchOrderNumber'
-- ORDER BY TABLE_NAME, COLUMN_NAME;
--
-- Expected result: one nullable VARCHAR(255) branchOrderNumber column.
--
-- Rollback, if needed before any dependent deployment relies on this field:
-- ALTER TABLE order_intent_submission
--     DROP COLUMN branchOrderNumber;
