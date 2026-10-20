package com.modernshopflow.catalog.receiving;

import com.modernshopflow.catalog.domain.CatalogConflictException;
import com.modernshopflow.catalog.domain.CatalogNotFoundException;
import com.modernshopflow.catalog.inventory.InventoryRepository;
import com.modernshopflow.catalog.inventory.InventoryRepository.LockedInventory;
import com.modernshopflow.catalog.receiving.ReceivingDtos.ReceivingRequest;
import com.modernshopflow.catalog.receiving.ReceivingDtos.ReceivingResponse;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Receives stock atomically with the inventory update and audit movement. */
@Service
public class ReceivingService {
    private final InventoryRepository inventoryRepository;
    private final ReceivingRepository receivingRepository;

    public ReceivingService(InventoryRepository inventoryRepository, ReceivingRepository receivingRepository) {
        this.inventoryRepository = inventoryRepository;
        this.receivingRepository = receivingRepository;
    }

    @Transactional
    public ReceivingResponse receive(ReceivingRequest request) {
        if (request.unitCost() == null || request.unitCost().signum() < 0 || request.unitCost().stripTrailingZeros().scale() > 0
                || request.unitCost().compareTo(new java.math.BigDecimal("9999999999")) > 0) {
            throw new IllegalArgumentException("Giá nhập phải là số nguyên VND từ 0 đến 9.999.999.999.");
        }
        if (request.quantity() == null || request.quantity() < 1) throw new IllegalArgumentException("Số lượng nhập phải lớn hơn 0.");
        try { request.unitCost().multiply(java.math.BigDecimal.valueOf(request.quantity())).longValueExact(); }
        catch (ArithmeticException error) { throw new IllegalArgumentException("Tổng tiền nhập vượt giới hạn hỗ trợ."); }
        LockedInventory inventory;
        try {
            inventory = inventoryRepository.lock(request.variantId());
        } catch (EmptyResultDataAccessException exception) {
            throw new CatalogNotFoundException("Product variant was not found");
        }

        int updated;
        try {
            updated = Math.addExact(inventory.quantity(), request.quantity());
        } catch (ArithmeticException exception) {
            throw new CatalogConflictException("Inventory quantity exceeds supported range");
        }
        inventoryRepository.updateQuantity(inventory.inventoryId(), updated);
        String supplier = normalize(request.supplierName());
        String note = normalize(request.note());
        long receivingId = receivingRepository.insert(request.variantId(), request.quantity(), supplier, note, request.unitCost());
        receivingRepository.updateLatestCost(request.variantId(), request.unitCost());
        receivingRepository.insertMovement(request.variantId(), request.quantity(), receivingId, note);
        return receivingRepository.get(receivingId);
    }

    public List<ReceivingResponse> list(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return receivingRepository.list(limit);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
