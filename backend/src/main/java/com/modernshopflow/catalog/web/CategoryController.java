package com.modernshopflow.catalog.web;

import com.modernshopflow.catalog.api.CategoryRequest;
import com.modernshopflow.catalog.api.CategoryResponse;
import com.modernshopflow.catalog.api.PageResponse;
import com.modernshopflow.catalog.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CatalogService service;

    public CategoryController(CatalogService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<CategoryResponse> listCategories(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer limit) {
        int requestedPage = page == null ? 0 : page;
        int requestedSize = size != null ? size : limit != null ? limit : 20;
        return PageResponse.fromPageResult(
                service.listCategories(requestedPage, requestedSize), requestedPage, requestedSize);
    }

    @GetMapping("/{id}")
    public CategoryResponse getCategory(@PathVariable long id) {
        return service.getCategory(id);
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCategory(request));
    }

    @PutMapping("/{id}")
    public CategoryResponse updateCategory(
            @PathVariable long id, @Valid @RequestBody CategoryRequest request) {
        return service.updateCategory(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable long id) {
        service.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
