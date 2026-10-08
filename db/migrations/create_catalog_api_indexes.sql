-- Indexes used by the Sprint 2 catalog APIs.
CREATE INDEX IF NOT EXISTS idx_products_active_category_id
    ON products(category_id) WHERE is_deleted = FALSE;

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_products_active_name_trgm
    ON products USING GIN (LOWER(name) gin_trgm_ops) WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_stock_holds_inventory_status_expire
    ON stock_holds(inventory_item_id, status, expires_at);
