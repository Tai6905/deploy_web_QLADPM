-- Preserve order/payment snapshots and add one immutable cancellation event per order.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_status;
ALTER TABLE orders ADD CONSTRAINT chk_orders_status
    CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'PAYMENT_FAILED', 'CANCELLED'));
ALTER TABLE payment_attempts DROP CONSTRAINT IF EXISTS chk_payment_status;
ALTER TABLE payment_attempts ADD CONSTRAINT chk_payment_status
    CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'EXPIRED', 'CANCELLED', 'REFUNDED'));
ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS chk_stock_movement_type;
ALTER TABLE stock_movements ADD CONSTRAINT chk_stock_movement_type CHECK (
    movement_type IN ('MANUAL_ADJUSTMENT', 'STOCK_RECEIVED', 'ORDER_RESERVED',
        'PAYMENT_FAILED_RELEASE', 'DELIVERY_COMPLETED', 'RETURN_RESTOCK', 'ORDER_CANCELLED_RELEASE')
);
CREATE TABLE IF NOT EXISTS order_cancellations (
    order_id BIGINT PRIMARY KEY REFERENCES orders(id),
    reason VARCHAR(500) NOT NULL,
    cancelled_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    actor_kind VARCHAR(20) NOT NULL CHECK (actor_kind IN ('CUSTOMER','STAFF')),
    cancelled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    previous_status VARCHAR(30) NOT NULL,
    previous_delivery_status VARCHAR(30) NOT NULL,
    payment_outcome VARCHAR(30) NOT NULL CHECK (payment_outcome IN ('NOT_CHARGED','SIMULATED_REFUND')),
    refund_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (refund_amount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_order_cancellations_time ON order_cancellations(cancelled_at DESC,order_id DESC);
