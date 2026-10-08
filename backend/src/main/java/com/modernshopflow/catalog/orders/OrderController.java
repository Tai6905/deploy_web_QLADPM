package com.modernshopflow.catalog.orders;

import com.modernshopflow.catalog.api.PageResponse;
import com.modernshopflow.catalog.domain.PageResult;
import com.modernshopflow.catalog.orders.OrderDtos.CreateOrderRequest;
import com.modernshopflow.catalog.orders.OrderDtos.OrderResponse;
import com.modernshopflow.catalog.orders.OrderDtos.OrderSummary;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for public checkout and authenticated owner order listing. */
@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public PageResponse<OrderSummary> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<OrderSummary> result = service.list(page, size);
        return PageResponse.fromPageResult(result, page, size);
    }

    @GetMapping("/reference/{orderRef}")
    public OrderResponse byReference(@PathVariable UUID orderRef) {
        return service.getByReference(orderRef);
    }
}