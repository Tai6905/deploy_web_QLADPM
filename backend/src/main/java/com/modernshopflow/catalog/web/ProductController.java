package com.modernshopflow.catalog.web;

import com.modernshopflow.catalog.api.PageResponse;
import com.modernshopflow.catalog.api.ProductDetailResponse;
import com.modernshopflow.catalog.api.ProductListItem;
import com.modernshopflow.catalog.domain.PageResult;
import com.modernshopflow.catalog.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
            @RequestParam(required = false) String sort) {
        int requestedPage = page == null ? 0 : page;
        int requestedSize = size != null ? size : limit != null ? limit : 20;
        PageResult<ProductListItem> result = service.listProducts(
                requestedPage, requestedSize, categoryId, search, minPrice, maxPrice, rating, sort);
        return PageResponse.fromPageResult(result, requestedPage, requestedSize);
    }

    @GetMapping("/{id}")
    public ProductDetailResponse getProduct(@PathVariable long id) {
        return service.getProduct(id);
    }
}
