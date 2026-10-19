-- Threshold is a catalog setting; inventory remains variant-level.
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS low_stock_threshold INT NOT NULL DEFAULT 5;

CREATE TABLE receiving_records (
    id BIGSERIAL PRIMARY KEY,
    variant_id BIGINT NOT NULL REFERENCES product_variants(id),
    quantity INT NOT NULL,
    supplier_name VARCHAR(255),
    note VARCHAR(500),
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_receiving_quantity CHECK (quantity > 0)
);

CREATE TABLE stock_movements (
    id BIGSERIAL PRIMARY KEY,
    variant_id BIGINT NOT NULL REFERENCES product_variants(id),
    movement_type VARCHAR(40) NOT NULL,
    quantity INT NOT NULL,
    reference_type VARCHAR(30),
    reference_id BIGINT,
    note VARCHAR(500),
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_stock_movement_type CHECK (
        movement_type IN ('MANUAL_ADJUSTMENT', 'STOCK_RECEIVED', 'ORDER_RESERVED',
                          'PAYMENT_FAILED_RELEASE', 'DELIVERY_COMPLETED', 'RETURN_RESTOCK')
    ),
    CONSTRAINT chk_stock_movement_quantity CHECK (quantity <> 0)
);

CREATE INDEX idx_receiving_variant_created ON receiving_records(variant_id, created_at DESC);
CREATE INDEX idx_stock_movements_variant_created ON stock_movements(variant_id, created_at DESC);
CREATE INDEX idx_stock_movements_reference ON stock_movements(reference_type, reference_id);