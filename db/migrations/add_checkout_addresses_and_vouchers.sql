ALTER TABLE orders ADD COLUMN subtotal_amount NUMERIC(12,2);
UPDATE orders SET subtotal_amount = total_amount;
-- Nullable during the rollout so an older deployed backend can still insert CARD orders.
ALTER TABLE orders ADD COLUMN discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN voucher_code VARCHAR(50);
ALTER TABLE orders ADD COLUMN checkout_key UUID UNIQUE;
ALTER TABLE orders ADD COLUMN checkout_fingerprint VARCHAR(64);
ALTER TABLE orders ADD CONSTRAINT chk_checkout_amounts CHECK (subtotal_amount >= 0 AND discount_amount >= 0 AND discount_amount <= subtotal_amount AND total_amount = subtotal_amount - discount_amount);
ALTER TABLE orders DROP CONSTRAINT chk_orders_payment_method;
ALTER TABLE orders ADD CONSTRAINT chk_orders_payment_method CHECK (payment_method IN ('CARD','BANK_TRANSFER','COD'));
ALTER TABLE payment_attempts DROP CONSTRAINT chk_payment_method;
ALTER TABLE payment_attempts ADD CONSTRAINT chk_payment_method CHECK (method IN ('CARD','BANK_TRANSFER','COD'));
CREATE INDEX idx_addresses_customer_default ON user_addresses(user_id,is_default DESC,id DESC);
-- Fixed dates make repeated initialization deterministic. These are project demo offers.
INSERT INTO vouchers(code,title,discount_type,discount_value,max_discount_amount,min_order_value,max_usages_total,max_usages_per_user,valid_from,valid_to)
VALUES ('MOC10','Mộc chào bạn · giảm 10%, tối đa 50.000đ','PERCENTAGE',10,50000,150000,10000,3,'2026-01-01T00:00:00Z','2030-12-31T23:59:59Z'),
       ('MOC30K','Góc Mộc mới · giảm 30.000đ từ 300.000đ','FIXED_AMOUNT',30000,NULL,300000,10000,3,'2026-01-01T00:00:00Z','2030-12-31T23:59:59Z')
ON CONFLICT (code) DO NOTHING;
