package com.modernshopflow.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.modernshopflow.catalog.api.CategoryResponse;
import com.modernshopflow.catalog.domain.CatalogConflictException;
import com.modernshopflow.catalog.repository.CatalogRepository;
import java.time.OffsetDateTime;
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

    private CategoryResponse category(long id, String name, String slug) {
        OffsetDateTime now = OffsetDateTime.now();
        return new CategoryResponse(id, name, slug, null, now, now);
    }
}
