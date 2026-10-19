-- Return requests reference purchased order lines and never mutate stock until RESTOCKED.
CREATE TABLE return_requests (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    reason VARCHAR(500),
    restockable BOOLEAN,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_return_status CHECK (status IN ('REQUESTED', 'APPROVED', 'RESTOCKED', 'REJECTED'))
);

CREATE TABLE return_request_items (
    id BIGSERIAL PRIMARY KEY,
    return_id BIGINT NOT NULL REFERENCES return_requests(id) ON DELETE CASCADE,
    order_item_id BIGINT NOT NULL REFERENCES order_items(id),
    quantity INT NOT NULL,
    CONSTRAINT chk_return_item_quantity CHECK (quantity > 0),
    CONSTRAINT uq_return_order_item UNIQUE (return_id, order_item_id)
);

CREATE INDEX idx_returns_order_created ON return_requests(order_id, created_at DESC);
CREATE INDEX idx_returns_status_created ON return_requests(status, created_at DESC);