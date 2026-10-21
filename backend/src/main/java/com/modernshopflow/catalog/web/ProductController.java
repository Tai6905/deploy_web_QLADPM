package com.modernshopflow.catalog.web;

import com.modernshopflow.catalog.api.PageResponse;
import com.modernshopflow.catalog.api.ProductCreateRequest;
import com.modernshopflow.catalog.api.ProductDetailResponse;
import com.modernshopflow.catalog.api.ProductListItem;
import com.modernshopflow.catalog.domain.PageResult;
import com.modernshopflow.catalog.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final CatalogService service;

    public ProductController(CatalogService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProductListItem> listProducts(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(name = "q", required = false) String search,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam java.util.Map<String, String> parameters) {
        int requestedPage = page == null ? 0 : page;
        int requestedSize = size != null ? size : limit != null ? limit : 20;
        var specifications = new java.util.LinkedHashMap<String, String>();
        parameters.forEach((key, value) -> {
            if (key.equals("instrumentType")) specifications.put(key, value);
            else if (key.startsWith("spec.")) specifications.put(key.substring(5), value);
        });
        PageResult<ProductListItem> result = service.listProducts(
                requestedPage, requestedSize, categoryId, search, minPrice, maxPrice, rating, sort, inStock, specifications);
        return PageResponse.fromPageResult(result, requestedPage, requestedSize);
    }

    @GetMapping("/{id}")
    public ProductDetailResponse getProduct(@PathVariable long id) {
        return service.getProduct(id);
    }

    @PostMapping
    public ResponseEntity<ProductDetailResponse> createProduct(
            @Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createProduct(request));
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}/content")
    public ProductDetailResponse updateContent(@PathVariable long id,
            @Valid @RequestBody com.modernshopflow.catalog.api.ProductContentRequest request) {
        return service.updateContent(id,request);
    }
}
