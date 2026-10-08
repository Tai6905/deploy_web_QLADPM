package com.modernshopflow.catalog.repository;

import com.modernshopflow.catalog.api.CategoryResponse;
import com.modernshopflow.catalog.api.ProductDetailResponse;
import com.modernshopflow.catalog.api.ProductListItem;
import java.math.BigDecimal;
import com.modernshopflow.catalog.domain.PageResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {

    private final JdbcTemplate jdbc;

    public CatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<CategoryResponse> findCategories(int size, int offset) {
        String sql = """
                SELECT id, name, slug, description, created_at, updated_at,
                       COUNT(*) OVER() AS total_items
                FROM categories
                WHERE is_deleted = FALSE
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """;
        List<CategoryResponseWithTotal> rows = jdbc.query(sql, this::mapCategoryWithTotal, size, offset);
        return new PageResult<>(
                rows.stream().map(CategoryResponseWithTotal::category).toList(),
                rows.isEmpty() ? 0 : rows.getFirst().totalItems());
    }

    public Optional<CategoryResponse> findCategory(long id) {
        String sql = """
                SELECT id, name, slug, description, created_at, updated_at
                FROM categories WHERE id = ? AND is_deleted = FALSE
                """;
        return jdbc.query(sql, this::mapCategory, id).stream().findFirst();
    }

    public long insertCategory(String name, String slug, String description) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO categories(name, slug, description) VALUES (?, ?, ?)",
                    new String[] {"id"});
            statement.setString(1, name);
            statement.setString(2, slug);
            statement.setString(3, description);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public int updateCategory(long id, String name, String slug, String description) {
        return jdbc.update(
                "UPDATE categories SET name = ?, slug = ?, description = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ? AND is_deleted = FALSE",
                name,
                slug,
                description,
                id);
    }

    public int softDeleteCategory(long id) {
        return jdbc.update(
                "UPDATE categories SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ? AND is_deleted = FALSE",
                id);
    }

    public long countActiveProductsInCategory(long categoryId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM products WHERE category_id = ? AND is_deleted = FALSE",
                Long.class,
                categoryId);
        return count == null ? 0 : count;
    }

    public PageResult<ProductListItem> findProducts(
            int size,
            int offset,
            Long categoryId,
            String search,
            Long minPrice,
            Long maxPrice,
            String sort) {
        // Optional filters remain JDBC parameters so user input never becomes SQL text.
        String sql = """
                                SELECT p.id, p.category_id, c.name AS category_name, p.sku, p.name,
                                           p.slug, p.description, p.list_price,
                                           COALESCE((
                                                   SELECT SUM(GREATEST(
                                                           COALESCE(ii.quantity, 0)
                                                           - COALESCE(ii.reserved_quantity, 0)
                                                           - COALESCE((
                                                                   SELECT SUM(sh.quantity)
                                                                   FROM stock_holds sh
                                                                   WHERE sh.inventory_item_id = ii.id
                                                                         AND sh.status = 'HOLDING'
                                                                         AND sh.expires_at > CURRENT_TIMESTAMP
                                                           ), 0),
                                                           0
                                                   ))
                                                   FROM product_variants pv
                                                   LEFT JOIN inventory_items ii ON ii.variant_id = pv.id
                                                   WHERE pv.product_id = p.id AND pv.is_deleted = FALSE
                                           ), 0) AS available_quantity,
                                           (
                                                   SELECT pi.image_url
                                                   FROM product_images pi
                                                   WHERE pi.product_id = p.id
                                                   ORDER BY pi.is_primary DESC, pi.display_order, pi.id
                                                   LIMIT 1
                                           ) AS image_url,
                                           COUNT(*) OVER() AS total_items
                FROM products p
                JOIN categories c ON c.id = p.category_id AND c.is_deleted = FALSE
                WHERE p.is_deleted = FALSE
                  AND (CAST(? AS BIGINT) IS NULL OR p.category_id = CAST(? AS BIGINT))
                  AND (CAST(? AS TEXT) IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%%', CAST(? AS TEXT), '%%')))
                  AND (CAST(? AS NUMERIC) IS NULL OR p.list_price >= CAST(? AS NUMERIC))
                  AND (CAST(? AS NUMERIC) IS NULL OR p.list_price <= CAST(? AS NUMERIC))
                ORDER BY %s
                LIMIT ? OFFSET ?
                """.formatted(orderClause(sort));
        List<ProductListWithTotal> rows = jdbc.query(
                sql,
                this::mapProductListWithTotal,
                categoryId,
                categoryId,
                search,
                search,
                minPrice,
                minPrice,
                maxPrice,
                maxPrice,
                size,
                offset);
        return new PageResult<>(
                rows.stream().map(ProductListWithTotal::product).toList(),
                rows.isEmpty() ? 0 : rows.getFirst().totalItems());
    }

    public Optional<ProductDetailResponse> findProduct(long id) {
        String productSql = """
                SELECT p.id, p.category_id, c.name AS category_name, p.sku, p.name,
                       p.slug, p.description, p.cost_price, p.list_price,
                       p.created_at, p.updated_at
                FROM products p
                JOIN categories c ON c.id = p.category_id AND c.is_deleted = FALSE
                WHERE p.id = ? AND p.is_deleted = FALSE
                """;
        List<ProductBase> products = jdbc.query(productSql, this::mapProductBase, id);
        if (products.isEmpty()) {
            return Optional.empty();
        }
        ProductBase product = products.getFirst();

        // Only unexpired HOLDING rows affect realtime availability.
        String variantsSql = """
                SELECT pv.id, pv.color, pv.size,
                       COALESCE(ii.quantity, 0) AS quantity,
                       COALESCE(ii.reserved_quantity, 0) AS reserved_quantity,
                       COALESCE(SUM(CASE WHEN sh.status = 'HOLDING'
                                         AND sh.expires_at > CURRENT_TIMESTAMP
                                         THEN sh.quantity ELSE 0 END), 0) AS holding_quantity
                FROM product_variants pv
                LEFT JOIN inventory_items ii ON ii.variant_id = pv.id
                LEFT JOIN stock_holds sh ON sh.inventory_item_id = ii.id
                WHERE pv.product_id = ? AND pv.is_deleted = FALSE
                GROUP BY pv.id, pv.color, pv.size, ii.quantity, ii.reserved_quantity
                ORDER BY pv.id
                """;
        List<ProductDetailResponse.ProductVariantResponse> variants =
                jdbc.query(variantsSql, this::mapVariant, id);

        String imagesSql = """
                SELECT id, image_url, display_order, is_primary
                FROM product_images WHERE product_id = ? ORDER BY display_order, id
                """;
        List<ProductDetailResponse.ProductImageResponse> images =
                jdbc.query(imagesSql, this::mapImage, id);

        return Optional.of(new ProductDetailResponse(
                product.id(),
                product.categoryId(),
                product.categoryName(),
                product.sku(),
                product.name(),
                product.slug(),
                product.description(),
                product.costPrice(),
                product.listPrice(),
                product.createdAt(),
                product.updatedAt(),
                variants,
                images));
    }

    private CategoryResponseWithTotal mapCategoryWithTotal(ResultSet rs, int rowNum)
            throws SQLException {
        return new CategoryResponseWithTotal(mapCategory(rs, rowNum), rs.getLong("total_items"));
    }

    private CategoryResponse mapCategory(ResultSet rs, int rowNum) throws SQLException {
        return new CategoryResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("description"),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class));
    }

    private ProductListWithTotal mapProductListWithTotal(ResultSet rs, int rowNum)
            throws SQLException {
        return new ProductListWithTotal(
                new ProductListItem(
                        rs.getLong("id"),
                        rs.getLong("category_id"),
                        rs.getString("category_name"),
                        rs.getString("sku"),
                        rs.getString("name"),
                        rs.getString("slug"),
                        rs.getString("description"),
                        integerAmount(rs, "list_price"),
                        rs.getLong("available_quantity"),
                        rs.getString("image_url")),
                rs.getLong("total_items"));
    }

    private ProductBase mapProductBase(ResultSet rs, int rowNum) throws SQLException {
        return new ProductBase(
                rs.getLong("id"),
                rs.getLong("category_id"),
                rs.getString("category_name"),
                rs.getString("sku"),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("description"),
                integerAmount(rs, "cost_price"),
                integerAmount(rs, "list_price"),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class));
    }

    private long integerAmount(ResultSet rs, String column) throws SQLException {
        BigDecimal amount = rs.getBigDecimal(column);
        try {
            return amount.longValueExact();
        } catch (ArithmeticException | NullPointerException exception) {
            throw new SQLException(column + " must be a whole VND amount", exception);
        }
    }

    private String orderClause(String sort) {
        return switch (sort) {
            case "priceAsc" -> "p.list_price ASC, p.id DESC";
            case "priceDesc" -> "p.list_price DESC, p.id DESC";
            case "newest" -> "p.created_at DESC, p.id DESC";
            default -> throw new IllegalArgumentException("Unsupported product sort: " + sort);
        };
    }

    private ProductDetailResponse.ProductVariantResponse mapVariant(ResultSet rs, int rowNum)
            throws SQLException {
        int quantity = rs.getInt("quantity");
        int reserved = rs.getInt("reserved_quantity");
        int holding = rs.getInt("holding_quantity");
        return new ProductDetailResponse.ProductVariantResponse(
                rs.getLong("id"),
                rs.getString("color"),
                rs.getString("size"),
                quantity,
                reserved,
                holding,
                Math.max(quantity - reserved - holding, 0));
    }

    private ProductDetailResponse.ProductImageResponse mapImage(ResultSet rs, int rowNum)
            throws SQLException {
        return new ProductDetailResponse.ProductImageResponse(
                rs.getLong("id"),
                rs.getString("image_url"),
                rs.getInt("display_order"),
                rs.getBoolean("is_primary"));
    }

    private record CategoryResponseWithTotal(CategoryResponse category, long totalItems) {}

    private record ProductListWithTotal(ProductListItem product, long totalItems) {}

    private record ProductBase(
            Long id,
            Long categoryId,
            String categoryName,
            String sku,
            String name,
            String slug,
            String description,
            long costPrice,
            long listPrice,
            java.time.OffsetDateTime createdAt,
            java.time.OffsetDateTime updatedAt) {}
}
