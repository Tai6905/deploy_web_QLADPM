package com.modernshopflow.catalog.inventory;

import com.modernshopflow.catalog.inventory.InventoryDtos.AdjustmentRequest;
import com.modernshopflow.catalog.inventory.InventoryDtos.InventoryItem;
import com.modernshopflow.catalog.inventory.InventoryDtos.ThresholdRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Role-protected inventory endpoints used by warehouse workspaces. */
@RestController
@RequestMapping("/inventory")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<InventoryItem> list() {
        return service.list();
    }

    @PostMapping("/variants/{variantId}/adjustments")
    public InventoryItem adjust(
            @PathVariable long variantId,
            @Valid @RequestBody AdjustmentRequest request) {
        return service.adjust(variantId, request);
    }

    @PutMapping("/products/{productId}/threshold")
    public ResponseEntity<Void> updateThreshold(
            @PathVariable long productId,
            @Valid @RequestBody ThresholdRequest request) {
        service.updateThreshold(productId, request);
        return ResponseEntity.noContent().build();
    }
}
