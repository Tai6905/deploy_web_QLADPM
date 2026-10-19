package com.modernshopflow.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.modernshopflow.catalog.api.CategoryResponse;
import com.modernshopflow.catalog.api.ProductCreateRequest;
import com.modernshopflow.catalog.api.ProductDetailResponse;
import com.modernshopflow.catalog.domain.CatalogConflictException;
import com.modernshopflow.catalog.repository.CatalogRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private CatalogRepository repository;

    @InjectMocks
    private CatalogService service;

    @Test
    void deletingCategoryWithActiveProductsReturnsConflict() {
        when(repository.findCategory(10L))
                .thenReturn(java.util.Optional.of(category(10L, "Phones", "phones")));
        when(repository.countActiveProductsInCategory(10L)).thenReturn(1L);

        assertThrows(CatalogConflictException.class, () -> service.deleteCategory(10L));
    }

    @Test
    void deletingEmptyCategorySoftDeletesIt() {
        when(repository.findCategory(11L))
                .thenReturn(java.util.Optional.of(category(11L, "Empty", "empty")));
        when(repository.countActiveProductsInCategory(11L)).thenReturn(0L);
        when(repository.softDeleteCategory(11L)).thenReturn(1);

        service.deleteCategory(11L);

        verify(repository).softDeleteCategory(11L);
    }

    @Test
    void invalidPageIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.listProducts(-1, 20, null, null, null, null, null, null));

        assertEquals("page must be >= 0", exception.getMessage());
    }

    @Test
    void pageOffsetOverflowIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.listProducts(Integer.MAX_VALUE, 2, null, null, null, null, null, null));

        assertEquals("page is too large for the requested size", exception.getMessage());
    }

    @Test
    void invalidPriceRangeIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.listProducts(0, 20, null, null, 800L, 500L, null, null));

        assertEquals("minPrice must be <= maxPrice", exception.getMessage());
    }

    @Test
    void ratingFilterIsRejectedUntilReviewDataExists() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.listProducts(0, 20, null, null, null, null, 4, null));

        assertEquals("rating filter requires review data", exception.getMessage());
    }

    @Test
    void fractionalVndPriceIsRejectedBeforeDatabaseWrite() {
        assertThrows(IllegalArgumentException.class, () -> service.createProduct(new ProductCreateRequest(
                12L, "MOC-1", "Wood Cup", null, null, new BigDecimal("100.50"))));
        verify(repository, never()).findCategory(anyLong());
    }

    @Test
    void creatingProductNormalizesSkuAndCreatesDefaultVariant() {
        when(repository.findCategory(12L))
                .thenReturn(Optional.of(category(12L, "Kitchen", "kitchen")));
        when(repository.insertProduct(
                12L,
                "MOC-1",
                "Wood Cup",
                "wood-cup-moc-1",
                null,
                BigDecimal.ZERO,
                BigDecimal.valueOf(25000)))
                .thenReturn(41L);
        when(repository.findProduct(41L)).thenReturn(Optional.of(product(41L)));

        ProductDetailResponse created = service.createProduct(new ProductCreateRequest(
                12L, " moc-1 ", " Wood Cup ", null, null, BigDecimal.valueOf(25000)));

        assertEquals(41L, created.id());
        verify(repository).insertDefaultVariant(41L);
    }

    @Test
    void creatingProductWithDuplicateSkuReturnsConflict() {
        when(repository.findCategory(12L))
                .thenReturn(Optional.of(category(12L, "Kitchen", "kitchen")));
        when(repository.insertProduct(
                12L,
                "MOC-1",
                "Wood Cup",
                "wood-cup-moc-1",
                null,
                BigDecimal.ZERO,
                BigDecimal.valueOf(25000)))
                .thenThrow(new DataIntegrityViolationException("duplicate SKU"));

        assertThrows(
                CatalogConflictException.class,
                () -> service.createProduct(new ProductCreateRequest(
                        12L, "moc-1", "Wood Cup", null, null, BigDecimal.valueOf(25000))));
        verify(repository, never()).insertDefaultVariant(anyLong());
    }

    private CategoryResponse category(long id, String name, String slug) {
        OffsetDateTime now = OffsetDateTime.now();
        return new CategoryResponse(id, name, slug, null, now, now);
    }

    private ProductDetailResponse product(long id) {
        OffsetDateTime now = OffsetDateTime.now();
        return new ProductDetailResponse(
                id,
                12L,
                "Kitchen",
                "MOC-1",
                "Wood Cup",
                "wood-cup-moc-1",
                null,
                0,
                25000,
                now,
                now,
                List.of(),
                List.of());
    }
}
