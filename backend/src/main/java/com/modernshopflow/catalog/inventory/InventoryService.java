package com.modernshopflow.catalog.inventory;

import com.modernshopflow.catalog.domain.CatalogConflictException;
import com.modernshopflow.catalog.inventory.InventoryDtos.AdjustmentRequest;
import com.modernshopflow.catalog.inventory.InventoryDtos.InventoryItem;
import com.modernshopflow.catalog.inventory.InventoryDtos.ThresholdRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies stock invariants and writes an audit movement with each manual change. */
@Service
public class InventoryService {
    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    public List<InventoryItem> list() {
        return repository.list();
    }

    @Transactional
    public InventoryItem adjust(long variantId, AdjustmentRequest request) {
        if (request.delta() == 0) throw new IllegalArgumentException("delta must not be zero");
        InventoryRepository.LockedInventory current = repository.lock(variantId);
        int updated;
        try {
            updated = Math.addExact(current.quantity(), request.delta());
        } catch (ArithmeticException exception) {
            throw new CatalogConflictException("Inventory quantity exceeds supported range");
        }
        if (updated < current.reserved() + current.holding()) {
            throw new CatalogConflictException("Adjustment would reduce available stock below reserved quantity");
        }
        repository.updateQuantity(current.inventoryId(), updated);
        repository.addMovement(variantId, "MANUAL_ADJUSTMENT", request.delta(), request.reason().trim());
        return repository.list().stream().filter(item -> item.variantId() == variantId).findFirst().orElseThrow();
    }

    @Transactional
    public void updateThreshold(long productId, ThresholdRequest request) {
        if (request.lowStockThreshold() < 0) {
            throw new IllegalArgumentException("lowStockThreshold must be >= 0");
        }
        repository.setProductThreshold(productId, request.lowStockThreshold());
    }
}