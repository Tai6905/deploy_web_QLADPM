-- Additive changes: preserve existing catalog, orders and receipts.
ALTER TABLE products ADD COLUMN IF NOT EXISTS usage_instructions TEXT;
-- Historical receipts have unknown costs; do not invent prices for them.
ALTER TABLE receiving_records ADD COLUMN IF NOT EXISTS unit_cost NUMERIC(12,0);
ALTER TABLE receiving_records ADD CONSTRAINT chk_receiving_unit_cost CHECK (unit_cost IS NULL OR unit_cost >= 0);
CREATE TABLE product_reviews (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_product_review_customer UNIQUE(product_id, user_id)
);
CREATE INDEX idx_product_reviews_product_created ON product_reviews(product_id, created_at DESC);
CREATE INDEX idx_products_price_active ON products(list_price, id) WHERE is_deleted = FALSE;
CREATE INDEX idx_orders_customer_purchase ON orders(customer_id, status, delivery_status);
