package com.shopflow.backend.controller;

import com.modernshopflow.catalog.api.PageResponse;
import com.modernshopflow.catalog.delivery.DeliveryDtos.DeliveryOrder;
import com.modernshopflow.catalog.delivery.DeliveryRepository;
import com.modernshopflow.catalog.orders.OrderDtos.OrderResponse;
import com.modernshopflow.catalog.orders.OrderDtos.OrderSummary;
import com.modernshopflow.catalog.orders.OrderService;
import com.modernshopflow.catalog.returns.ReturnRepository;
import com.modernshopflow.catalog.returns.ReturnService;
import com.modernshopflow.catalog.returns.ReturnDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import com.shopflow.backend.service.AccountService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account/orders")
public class CustomerOrderController {
    private final AccountService accounts;
    private final OrderService orders;
    private final DeliveryRepository deliveries;
    private final ReturnRepository returnRepository;
    private final ReturnService returns;
    public CustomerOrderController(AccountService accounts, OrderService orders, DeliveryRepository deliveries,
            ReturnRepository returnRepository, ReturnService returns) {
        this.accounts = accounts; this.orders = orders; this.deliveries = deliveries;
        this.returnRepository = returnRepository; this.returns = returns;
    }
    @GetMapping public PageResponse<OrderSummary> list(Authentication authentication,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(name = "q", required = false) String query) {
        var user = accounts.requireUser(authentication);
        return PageResponse.fromPageResult(orders.listMine(user.getId(), page, size, query), page, size);
    }
    @GetMapping("/{id}") public MyOrderDetail detail(Authentication authentication, @PathVariable long id) {
        var user = accounts.requireUser(authentication);
        var order = orders.getMine(id, user.getId());
        return new MyOrderDetail(order, deliveries.get(id), returnRepository.listReturnableItems(id), returnRepository.listForOrder(id));
    }
    @PostMapping("/{id}/returns") @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ReturnResponse requestReturn(Authentication authentication, @PathVariable long id, @Valid @RequestBody CustomerReturn request) {
        orders.getMine(id, accounts.requireUser(authentication).getId());
        return returns.create(new CreateReturnRequest(id, request.reason(), request.items()));
    }
    public record CustomerReturn(@NotBlank @Size(max = 500) String reason,
            @NotEmpty List<@NotNull @Valid ReturnItemRequest> items) {}
    public record MyOrderDetail(OrderResponse order, DeliveryOrder delivery, List<ReturnableItem> returnableItems, List<ReturnResponse> returns) {}
}
