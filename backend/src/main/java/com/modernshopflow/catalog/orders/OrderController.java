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
import org.springframework.security.core.Authentication;
import com.shopflow.backend.service.AccountService;
import com.modernshopflow.catalog.domain.CatalogNotFoundException;

/** HTTP boundary for public checkout and authenticated owner order listing. */
@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService service;
    private final AccountService accounts;

    public OrderController(OrderService service, AccountService accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request, Authentication authentication) {
        Long customerId = accounts.customerForOrder(authentication, request.customer().email());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, customerId));
    }

    @GetMapping
    public PageResponse<OrderSummary> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(name = "q", required = false) String search) {
        PageResult<OrderSummary> result = service.list(page, size, search);
        return PageResponse.fromPageResult(result, page, size);
    }

    @GetMapping("/reference/{orderRef}")
    public OrderResponse byReference(@PathVariable UUID orderRef, Authentication authentication,
            @RequestParam(required = false) String phone) {
        var user = accounts.optionalUser(authentication);
        if (user.isPresent()) {
            if (java.util.Set.of("SHOP_OWNER", "SALES_STAFF").contains(user.get().getRole())) return service.getByReference(orderRef);
            return service.getMineByReference(orderRef, user.get().getId());
        }
        var order = service.getByReference(orderRef);
        if (phone == null || !order.shippingAddress().phone().equals(phone.trim())) {
            throw new CatalogNotFoundException("Order was not found");
        }
        return order;
    }
}
