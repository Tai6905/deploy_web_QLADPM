-- Create vouchers table
CREATE TABLE vouchers (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value NUMERIC(12,2) NOT NULL,
    max_discount_amount NUMERIC(12,2),
    min_order_value NUMERIC(12,2) NOT NULL DEFAULT 0,
    max_usages_total INT,
    max_usages_per_user INT NOT NULL DEFAULT 1,
    current_usage_count INT NOT NULL DEFAULT 0,
    valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_to TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_vouchers_code UNIQUE (code),
    CONSTRAINT chk_vouchers_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT chk_vouchers_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'EXPIRED')),
    CONSTRAINT chk_vouchers_discount_value CHECK (discount_value > 0),
    CONSTRAINT chk_vouchers_min_order CHECK (min_order_value >= 0)
);

-- Create voucher_usages table
CREATE TABLE voucher_usages (
    id BIGSERIAL PRIMARY KEY,
    voucher_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    discount_amount NUMERIC(12,2) NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_usages_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id),
    CONSTRAINT fk_usages_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_usages_order UNIQUE (order_id),
    CONSTRAINT chk_usages_discount_amount CHECK (discount_amount >= 0)
);

-- Create Indexes
CREATE INDEX idx_vouchers_status_time ON vouchers(status, valid_from, valid_to);
CREATE INDEX idx_usages_user_voucher ON voucher_usages(user_id, voucher_id);