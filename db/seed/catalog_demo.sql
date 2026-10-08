-- Demo data for local verification of Tasks 43, 47 and 51.
-- Run after the application has completed database schema initialization.

INSERT INTO categories (name, slug, description)
VALUES ('Điện thoại', 'dien-thoai', 'Thiết bị di động')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO categories (name, slug, description)
VALUES ('Phụ kiện', 'phu-kien', 'Phụ kiện công nghệ')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO products (category_id, sku, name, slug, description, cost_price, list_price)
SELECT id, 'DEMO-PHONE-001', 'Modern Phone X', 'modern-phone-x',
       'Sản phẩm demo cho API chi tiết', 500.00, 799.00
FROM categories WHERE slug = 'dien-thoai'
ON CONFLICT (sku) DO NOTHING;

INSERT INTO products (category_id, sku, name, slug, description, cost_price, list_price)
SELECT id, 'DEMO-CASE-001', 'Modern Phone Case', 'modern-phone-case',
       'Sản phẩm demo cho API danh sách', 10.00, 29.00
FROM categories WHERE slug = 'phu-kien'
ON CONFLICT (sku) DO NOTHING;

INSERT INTO product_variants (product_id, color, size)
SELECT id, 'Black', '128GB'
FROM products WHERE sku = 'DEMO-PHONE-001'
  AND NOT EXISTS (
      SELECT 1 FROM product_variants pv
      WHERE pv.product_id = products.id AND pv.color = 'Black' AND pv.size = '128GB'
  );

INSERT INTO product_variants (product_id, color, size)
SELECT id, 'Blue', '256GB'
FROM products WHERE sku = 'DEMO-PHONE-001'
  AND NOT EXISTS (
      SELECT 1 FROM product_variants pv
      WHERE pv.product_id = products.id AND pv.color = 'Blue' AND pv.size = '256GB'
  );

INSERT INTO product_images (product_id, image_url, display_order, is_primary)
SELECT id, 'https://cdn.example.test/modern-phone-x/front.jpg', 0, TRUE
FROM products WHERE sku = 'DEMO-PHONE-001'
  AND NOT EXISTS (
      SELECT 1 FROM product_images pi
      WHERE pi.product_id = products.id AND pi.image_url = 'https://cdn.example.test/modern-phone-x/front.jpg'
  );

INSERT INTO inventory_items (variant_id, quantity, reserved_quantity)
SELECT pv.id, 10, 2
FROM product_variants pv
JOIN products p ON p.id = pv.product_id
WHERE p.sku = 'DEMO-PHONE-001' AND pv.color = 'Black'
ON CONFLICT (variant_id) DO UPDATE SET quantity = EXCLUDED.quantity,
    reserved_quantity = EXCLUDED.reserved_quantity;

INSERT INTO inventory_items (variant_id, quantity, reserved_quantity)
SELECT pv.id, 4, 0
FROM product_variants pv
JOIN products p ON p.id = pv.product_id
WHERE p.sku = 'DEMO-PHONE-001' AND pv.color = 'Blue'
ON CONFLICT (variant_id) DO UPDATE SET quantity = EXCLUDED.quantity,
    reserved_quantity = EXCLUDED.reserved_quantity;

INSERT INTO stock_holds (inventory_item_id, quantity, status, expires_at)
SELECT ii.id, 3, 'HOLDING', CURRENT_TIMESTAMP + INTERVAL '15 minutes'
FROM inventory_items ii
JOIN product_variants pv ON pv.id = ii.variant_id
JOIN products p ON p.id = pv.product_id
WHERE p.sku = 'DEMO-PHONE-001' AND pv.color = 'Black'
  AND NOT EXISTS (
      SELECT 1 FROM stock_holds sh
      WHERE sh.inventory_item_id = ii.id AND sh.status = 'HOLDING'
        AND sh.expires_at > CURRENT_TIMESTAMP
  );
