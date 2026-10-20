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
                rows.isEmpty() ? jdbc.queryForObject(
                        "SELECT COUNT(*) FROM categories WHERE is_deleted = FALSE", Long.class)
                        : rows.getFirst().totalItems());
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

    public long insertProduct(
            long categoryId,
            String sku,
            String name,
            String slug,
            String description,
            BigDecimal costPrice,
            BigDecimal listPrice) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO products(
                        category_id, sku, name, slug, description, cost_price, list_price
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    new String[] {"id"});
            statement.setLong(1, categoryId);
            statement.setString(2, sku);
            statement.setString(3, name);
            statement.setString(4, slug);
            statement.setString(5, description);
            statement.setBigDecimal(6, costPrice);
            statement.setBigDecimal(7, listPrice);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public long insertDefaultVariant(long productId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO product_variants(product_id) VALUES (?)",
                    new String[] {"id"});
            statement.setLong(1, productId);
            return statement;
        }, keyHolder);
        long variantId = keyHolder.getKey().longValue();
        jdbc.update(
                "INSERT INTO inventory_items(variant_id, quantity, reserved_quantity) VALUES (?, 0, 0)",
                variantId);
        return variantId;
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

    public PageResult<ProductListItem> findProducts(int size, int offset, Long categoryId, String search, Long minPrice, Long maxPrice, String sort) {
        return findProducts(size, offset, categoryId, search, minPrice, maxPrice, sort, null, null);
    }

    public PageResult<ProductListItem> findProducts(int size, int offset, Long categoryId, String search,
            Long minPrice, Long maxPrice, String sort, Integer rating, Boolean inStock) {
        String filters = """
                FROM catalog p WHERE (CAST(? AS BIGINT) IS NULL OR p.category_id = ?)
                  AND (CAST(? AS TEXT) IS NULL OR TRANSLATE(LOWER(CONCAT(p.name, ' ', p.sku, ' ', p.category_name)),
                    'àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ',
                    'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd') LIKE CONCAT('%', CAST(? AS TEXT), '%'))
                  AND (CAST(? AS NUMERIC) IS NULL OR p.list_price >= ?)
                  AND (CAST(? AS NUMERIC) IS NULL OR p.list_price <= ?)
                  AND (CAST(? AS INT) IS NULL OR p.average_rating >= ?)
                  AND (CAST(? AS BOOLEAN) IS NULL OR (p.available_quantity > 0) = ?)
                """;
        Object[] params = {categoryId, categoryId, search, search, minPrice, minPrice, maxPrice, maxPrice, rating, rating, inStock, inStock};
        var args = new java.util.ArrayList<Object>(java.util.Arrays.asList(params));
        args.add(size); args.add(offset);
        var rows = jdbc.query(CATALOG_SQL + "SELECT p.*, COUNT(*) OVER() AS total_items " + filters
                + " ORDER BY " + orderClause(sort) + " LIMIT ? OFFSET ?", this::mapProductListWithTotal, args.toArray());
        long total = rows.isEmpty() ? jdbc.queryForObject(CATALOG_SQL + "SELECT COUNT(*) " + filters, Long.class, params) : rows.getFirst().totalItems();
        return new PageResult<>(rows.stream().map(ProductListWithTotal::product).toList(), total);
    }

    private static final String CATALOG_SQL = """
            WITH holds AS (
                SELECT inventory_item_id, SUM(quantity) AS holding FROM stock_holds
                WHERE status='HOLDING' AND expires_at > CURRENT_TIMESTAMP GROUP BY inventory_item_id
            ), stock AS (
                SELECT pv.product_id, SUM(GREATEST(COALESCE(ii.quantity,0)-COALESCE(ii.reserved_quantity,0)-COALESCE(h.holding,0),0)) AS available_quantity
                FROM product_variants pv LEFT JOIN inventory_items ii ON ii.variant_id=pv.id
                LEFT JOIN holds h ON h.inventory_item_id=ii.id WHERE pv.is_deleted=FALSE GROUP BY pv.product_id
            ), ratings AS (
                SELECT product_id, AVG(CAST(rating AS NUMERIC)) AS average_rating, COUNT(*) AS review_count
                FROM product_reviews GROUP BY product_id
            ), catalog AS (
                SELECT p.id, p.category_id, c.name AS category_name, p.sku, p.name, p.slug, p.description, p.list_price,
                    COALESCE(s.available_quantity,0) AS available_quantity, COALESCE(r.average_rating,0) AS average_rating,
                    COALESCE(r.review_count,0) AS review_count,
                    (SELECT image_url FROM product_images pi WHERE pi.product_id=p.id ORDER BY is_primary DESC, display_order, id LIMIT 1) AS image_url
                FROM products p JOIN categories c ON c.id=p.category_id AND c.is_deleted=FALSE
                LEFT JOIN stock s ON s.product_id=p.id LEFT JOIN ratings r ON r.product_id=p.id WHERE p.is_deleted=FALSE
            )
            """;

    public void saveProductContent(long productId, String usageInstructions, List<String> images) {
        jdbc.update("UPDATE products SET usage_instructions = ? WHERE id = ?", usageInstructions, productId);
        for (int index = 0; index < images.size(); index++) {
            jdbc.update("INSERT INTO product_images(product_id, image_url, display_order, is_primary) VALUES (?, ?, ?, ?)",
                    productId, images.get(index), index, index == 0);
        }
    }

    public void updateProductContent(long productId, String description, String usageInstructions, List<String> images) {
        jdbc.update("UPDATE products SET description=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", description,productId);
        jdbc.update("DELETE FROM product_images WHERE product_id=?", productId);
        saveProductContent(productId,usageInstructions,images);
    }

    public Optional<ProductDetailResponse> findProduct(long id) {
        String productSql = """
                SELECT p.id, p.category_id, c.name AS category_name, p.sku, p.name,
                       p.slug, p.description, p.cost_price, p.list_price,
                       p.created_at, p.updated_at, p.usage_instructions,
                       COALESCE((SELECT AVG(CAST(rating AS NUMERIC)) FROM product_reviews WHERE product_id=p.id),0) AS average_rating,
                       (SELECT COUNT(*) FROM product_reviews WHERE product_id=p.id) AS review_count
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
                images, product.usageInstructions(), product.averageRating(), product.reviewCount()));
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
                        rs.getString("image_url"), rs.getDouble("average_rating"), rs.getLong("review_count")),
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
                rs.getObject("updated_at", java.time.OffsetDateTime.class),
                rs.getString("usage_instructions"), rs.getDouble("average_rating"), rs.getLong("review_count"));
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
            case "newest" -> "p.id DESC";
            case "rating" -> "p.average_rating DESC, p.review_count DESC, p.id DESC";
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
            java.time.OffsetDateTime updatedAt, String usageInstructions, double averageRating, long reviewCount) {}
}
