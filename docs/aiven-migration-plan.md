# Aiven schema update

The read-only connection check on 2026-10-19 found the five catalog/auth migrations already recorded in `schema_migrations` and two CUSTOMER accounts. No existing account is promoted or reset by these changes.

The four pending scripts, in dependency order, are:

1. `db/migrations/create_orders_and_payments.sql`: creates orders, order_items, payment_attempts and their indexes.
2. `db/migrations/create_delivery_status_history.sql`: creates delivery_status_history and its index.
3. `db/migrations/create_receiving_records_and_stock_movements.sql`: adds products.low_stock_threshold with default 5, creates receiving_records, stock_movements and their indexes.
4. `db/migrations/create_customer_returns.sql`: creates return_requests, return_request_items and their indexes.

The initializer records successful script names and commits the pending scripts together. If a script fails it rolls back that migration transaction. There are no DROP, TRUNCATE or DELETE statements in these four scripts. These are persistent schema changes, even though the subsequent business smoke test rolls its own fixture rows back. Sequence values consumed by rollback tests can leave normal gaps in IDs.

The online smoke test checks registration/password verification, product/category creation, stock receiving and holds, payment, delivery from NONE through DELIVERED, partial-return accounting, warehouse restocking, search, pagination and stock thresholds. It compares all affected table row counts before and after the rollback; all fixtures have random identifiers.

## Result on 2026-10-19

After explicit approval from the user, all four migrations completed successfully. All online smoke checks passed, including daily revenue aggregation. All test business rows were rolled back and counts in all 14 affected tables matched their values before the test transaction. The two original customer accounts remain unchanged. No permanent owner or warehouse test account was created; use the startup script's `-BootstrapOwner` option to choose a real owner email and password privately.
