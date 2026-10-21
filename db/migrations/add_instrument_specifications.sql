-- Additive, compatible with products created by older application versions.
CREATE TABLE IF NOT EXISTS product_specifications (
    product_id BIGINT NOT NULL REFERENCES products(id),
    spec_key VARCHAR(40) NOT NULL,
    spec_value VARCHAR(40) NOT NULL,
    PRIMARY KEY (product_id, spec_key)
);
CREATE INDEX IF NOT EXISTS idx_product_specs_filter
    ON product_specifications(spec_key, spec_value, product_id);
