package com.modernshopflow.catalog.inventory;

import com.modernshopflow.catalog.domain.CatalogNotFoundException;
import com.modernshopflow.catalog.inventory.InventoryDtos.InventoryItem;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Variant-level inventory SQL, including active checkout holds in available-stock calculations. */
@Repository
public class InventoryRepository {
    private final JdbcTemplate jdbc;

    public InventoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<InventoryItem> list() {
        return jdbc.query(
                """
                SELECT p.id AS product_id, pv.id AS variant_id, p.name AS product_name,
                       pv.color, pv.size, COALESCE(ii.quantity, 0) AS on_hand,
                       COALESCE(ii.reserved_quantity, 0) AS reserved,
                       COALESCE(holds.holding, 0) AS holding, p.low_stock_threshold
                FROM product_variants pv
                JOIN products p ON p.id = pv.product_id
                LEFT JOIN inventory_items ii ON ii.variant_id = pv.id
                LEFT JOIN LATERAL (
                    SELECT SUM(sh.quantity)::INT AS holding
                    FROM stock_holds sh
                    WHERE sh.inventory_item_id = ii.id AND sh.status = 'HOLDING'
                      AND sh.expires_at > CURRENT_TIMESTAMP
                ) holds ON TRUE
                WHERE pv.is_deleted = FALSE AND p.is_deleted = FALSE
                ORDER BY p.id, pv.id
                """,
                (rs, row) -> {
                    int onHand = rs.getInt("on_hand");
                    int reserved = rs.getInt("reserved");
                    int holding = rs.getInt("holding");
                    int available = Math.max(onHand - reserved - holding, 0);
                    int threshold = rs.getInt("low_stock_threshold");
                    return new InventoryItem(
                            rs.getLong("product_id"), rs.getLong("variant_id"), rs.getString("product_name"),
                            rs.getString("color"), rs.getString("size"), onHand, reserved, holding,
                            available, threshold, available <= threshold);
                });
    }

    public LockedInventory lock(long variantId) {
        jdbc.update("INSERT INTO inventory_items(variant_id) VALUES (?) ON CONFLICT (variant_id) DO NOTHING", variantId);
        return jdbc.queryForObject(
                """
                SELECT ii.id, ii.variant_id, ii.quantity, ii.reserved_quantity,
                       COALESCE((SELECT SUM(sh.quantity) FROM stock_holds sh
                           WHERE sh.inventory_item_id = ii.id AND sh.status = 'HOLDING'
                             AND sh.expires_at > CURRENT_TIMESTAMP), 0) AS holding
                FROM inventory_items ii JOIN product_variants pv ON pv.id = ii.variant_id
                JOIN products p ON p.id = pv.product_id
                WHERE ii.variant_id = ? AND pv.is_deleted = FALSE AND p.is_deleted = FALSE
                FOR UPDATE OF ii
                """,
                (rs, row) -> new LockedInventory(rs.getLong("id"), rs.getLong("variant_id"),
                        rs.getInt("quantity"), rs.getInt("reserved_quantity"), rs.getInt("holding")),
                variantId);
    }

    public void updateQuantity(long inventoryId, int quantity) {
        jdbc.update("UPDATE inventory_items SET quantity = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", quantity, inventoryId);
    }

    public void addMovement(long variantId, String type, int quantity, String note) {
        jdbc.update("INSERT INTO stock_movements(variant_id, movement_type, quantity, note) VALUES (?, ?, ?, ?)",
                variantId, type, quantity, note);
    }

    public void setProductThreshold(long productId, int threshold) {
        int updated = jdbc.update("UPDATE products SET low_stock_threshold = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = FALSE",
                threshold, productId);
        if (updated == 0) throw new CatalogNotFoundException("Product " + productId + " was not found");
    }

    public record LockedInventory(long inventoryId, long variantId, int quantity, int reserved, int holding) {}
}