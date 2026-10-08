-- Append-only history makes delivery transitions auditable and idempotent.
CREATE TABLE delivery_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status VARCHAR(20) NOT NULL,
    to_status VARCHAR(20) NOT NULL,
    changed_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_delivery_history_from CHECK (from_status IN ('NONE', 'PREPARING', 'SHIPPED', 'DELIVERED')),
    CONSTRAINT chk_delivery_history_to CHECK (to_status IN ('PREPARING', 'SHIPPED', 'DELIVERED'))
);

CREATE INDEX idx_delivery_history_order_time ON delivery_status_history(order_id, changed_at, id);