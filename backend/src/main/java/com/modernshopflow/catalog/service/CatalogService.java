package com.modernshopflow.catalog.service;

import com.modernshopflow.catalog.api.CategoryRequest;
import com.modernshopflow.catalog.api.CategoryResponse;
import com.modernshopflow.catalog.api.ProductCreateRequest;
import com.modernshopflow.catalog.api.ProductDetailResponse;
import com.modernshopflow.catalog.api.ProductListItem;
import com.modernshopflow.catalog.domain.CatalogConflictException;
import com.modernshopflow.catalog.domain.CatalogNotFoundException;
import com.modernshopflow.catalog.domain.PageResult;
import com.modernshopflow.catalog.repository.CatalogRepository;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final CatalogRepository repository;

    public CatalogService(CatalogRepository repository) {
        this.repository = repository;
    }

    public PageResult<CategoryResponse> listCategories(int page, int size) {
        return repository.findCategories(size, pageOffset(page, size));
    }

    public CategoryResponse getCategory(long id) {
        return repository.findCategory(id)
                .orElseThrow(() -> new CatalogNotFoundException("Category " + id + " was not found"));
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        try {
            long id = repository.insertCategory(request.name(), request.slug(), request.description());
            return getCategory(id);
        } catch (DataIntegrityViolationException exception) {
            throw new CatalogConflictException("Category slug already exists");
        }
    }

    @Transactional
    public CategoryResponse updateCategory(long id, CategoryRequest request) {
        getCategory(id);
        try {
            repository.updateCategory(id, request.name(), request.slug(), request.description());
            return getCategory(id);
        } catch (DataIntegrityViolationException exception) {
            throw new CatalogConflictException("Category slug already exists");
        }
    }

    @Transactional
    public void deleteCategory(long id) {
        getCategory(id);
        long productCount = repository.countActiveProductsInCategory(id);
        if (productCount > 0) {
            throw new CatalogConflictException(
                    "Category " + id + " cannot be deleted while it contains " + productCount
                            + " active product(s)");
        }
        if (repository.softDeleteCategory(id) == 0) {
            throw new CatalogNotFoundException("Category " + id + " was not found");
        }
    }

    public PageResult<ProductListItem> listProducts(
            int page,
            int size,
            Long categoryId,
            String search,
            Long minPrice,
            Long maxPrice,
            Integer rating,
            String sort) {
        validatePriceRange(minPrice, maxPrice);
        if (rating != null) {
            throw new IllegalArgumentException("rating filter requires review data");
        }
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        String normalizedSort = normalizeSort(sort);
        return repository.findProducts(
                size,
                pageOffset(page, size),
                categoryId,
                normalizedSearch,
                minPrice,
                maxPrice,
                normalizedSort);
    }

    public ProductDetailResponse getProduct(long id) {
        return repository.findProduct(id)
                .orElseThrow(() -> new CatalogNotFoundException("Product " + id + " was not found"));
    }

    @Transactional
    public ProductDetailResponse createProduct(ProductCreateRequest request) {
        if (request.listPrice() == null || request.listPrice().signum() <= 0
                || request.listPrice().stripTrailingZeros().scale() > 0
                || (request.costPrice() != null && (request.costPrice().signum() < 0
                    || request.costPrice().stripTrailingZeros().scale() > 0))) {
            throw new IllegalArgumentException("Prices must be whole VND amounts, with listPrice greater than zero");
        }
        CategoryResponse category = getCategory(request.categoryId());
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        String name = request.name().trim();
        String slug = slugify(name + " " + sku);
        if (slug.length() > 255) slug = slugify(name).substring(0, Math.min(150, slugify(name).length())) + "-" + slugify(sku);
        long productId;
        try {
            productId = repository.insertProduct(
                    category.id(),
                    sku,
                    name,
                    slug,
                    request.description() == null ? null : request.description().trim(),
                    request.costPrice() == null ? BigDecimal.ZERO : request.costPrice(),
                    request.listPrice());
        } catch (DataIntegrityViolationException exception) {
            throw new CatalogConflictException("Product SKU or slug already exists");
        }
        repository.insertDefaultVariant(productId);
        return getProduct(productId);
    }

    private String slugify(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private int pageOffset(int page, int size) {
        // Keep pagination arithmetic bounded before it reaches PostgreSQL OFFSET.
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        try {
            return Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("page is too large for the requested size", exception);
        }
    }

    private void validatePriceRange(Long minPrice, Long maxPrice) {
        if (minPrice != null && minPrice < 0) {
            throw new IllegalArgumentException("minPrice must be >= 0");
        }
        if (maxPrice != null && maxPrice < 0) {
            throw new IllegalArgumentException("maxPrice must be >= 0");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new IllegalArgumentException("minPrice must be <= maxPrice");
        }
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "newest";
        }
        return switch (sort) {
            case "priceAsc", "priceDesc", "newest" -> sort;
            case "rating" -> throw new IllegalArgumentException("rating sort requires review data");
            default -> throw new IllegalArgumentException(
                    "sort must be one of: priceAsc, priceDesc, newest");
        };
    }
}
