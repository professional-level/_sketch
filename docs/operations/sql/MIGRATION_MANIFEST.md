# Trading Runtime SQL Migration Manifest

Apply these MySQL migrations explicitly before running production-like profiles with `spring.jpa.hibernate.ddl-auto=validate` or `none`.

## 1. Root KIS Wrapper Token Store

```text
20260602_create_kis_access_token.mysql.sql
20260602_create_kis_token_refresh_lock.mysql.sql
```

## 2. Strategy Execution Service Base State

```text
20260602_create_strategy_execution_outbox_base.mysql.sql
20260602_create_strategy_execution_state_tables.mysql.sql
20260602_create_final_price_bating_v1_strategy_execution.mysql.sql
20260602_add_final_price_bating_v1_sell_lifecycle_columns.mysql.sql
```

## 3. Stock Purchase Service Base State

```text
20260602_create_stock_purchase_order_base_tables.mysql.sql
20260602_create_stock_purchase_legacy_order_tables.mysql.sql
20260602_create_stock_purchase_reconciliation_tables.mysql.sql
```

## 4. Cross-Service Operational Columns

```text
20260602_add_outbox_trace_columns.mysql.sql
20260602_add_outbox_next_attempt_at.mysql.sql
20260602_add_outbox_claim_lease.mysql.sql
20260602_add_order_intent_submission_branch_order_number.mysql.sql
20260602_add_order_intent_submission_exchange.mysql.sql
20260602_add_order_intent_submission_trading_environment.mysql.sql
20260602_add_order_intent_submission_market.mysql.sql
```

Run each file's preflight query first, apply its DDL, then run its verification query before continuing to the next file.
