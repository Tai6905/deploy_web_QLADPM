-- Orders store checkout snapshots so later product edits never rewrite old sales.
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_ref UUID NOT NULL UNIQUE,
    customer_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    customer_name VARCHAR(255) NOT NULL,
    customer_email VARCHAR(255),
    customer_phone VARCHAR(20),
    receiver_name VARCHAR(255) NOT NULL,
    receiver_phone VARCHAR(20) NOT NULL,
    address_line VARCHAR(500) NOT NULL,
    district VARCHAR(100),
    city VARCHAR(100) NOT NULL,
    payment_method VARCHAR(20) NOT NULL DEFAULT 'CARD',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    delivery_status VARCHAR(30) NOT NULL DEFAULT 'NONE',
    total_amount NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_orders_payment_method CHECK (payment_method = 'CARD'),
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'PAYMENT_FAILED')),
    CONSTRAINT chk_orders_delivery_status CHECK (delivery_status IN ('NONE', 'PREPARING', 'SHIPPED', 'DELIVERED')),
    CONSTRAINT chk_orders_total CHECK (total_amount >= 0)
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    variant_id BIGINT NOT NULL REFERENCES product_variants(id),
    product_name VARCHAR(255) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    quantity INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_order_items_price CHECK (unit_price >= 0),
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT uq_order_items_variant UNIQUE (order_id, variant_id)
);

CREATE TABLE payment_attempts (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    method VARCHAR(20) NOT NULL DEFAULT 'CARD',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    amount NUMERIC(12,2) NOT NULL,
    failure_reason VARCHAR(500),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_payment_method CHECK (method = 'CARD'),
    CONSTRAINT chk_payment_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'EXPIRED')),
    CONSTRAINT chk_payment_amount CHECK (amount >= 0)
);

CREATE INDEX idx_orders_created ON orders(created_at DESC, id DESC);
CREATE INDEX idx_orders_status_delivery ON orders(status, delivery_status);
CREATE INDEX idx_order_items_order ON order_items(order_id, id);