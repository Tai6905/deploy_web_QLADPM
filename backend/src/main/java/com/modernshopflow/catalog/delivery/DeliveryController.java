package com.modernshopflow.catalog.delivery;

import com.modernshopflow.catalog.delivery.DeliveryDtos.DeliveryOrder;
import com.modernshopflow.catalog.delivery.DeliveryDtos.UpdateDeliveryRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Role-protected delivery queue and forward-only status transition endpoint. */
@RestController
@RequestMapping
public class DeliveryController {
    private final DeliveryService service;

    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    @GetMapping("/deliveries")
    public List<DeliveryOrder> list() {
        return service.list();
    }

    @PatchMapping("/orders/{orderId}/delivery")
    public DeliveryOrder advance(@PathVariable long orderId, @Valid @RequestBody UpdateDeliveryRequest request) {
        if (request.toStatus() == null || request.toStatus().isBlank()) {
            throw new IllegalArgumentException("toStatus is required");
        }
        return service.advance(orderId, request.toStatus());
    }
}