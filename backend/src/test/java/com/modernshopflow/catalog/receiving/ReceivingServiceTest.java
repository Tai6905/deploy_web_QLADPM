package com.modernshopflow.catalog.receiving;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.modernshopflow.catalog.inventory.InventoryRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ReceivingServiceTest {
    @Test void receiptKeepsEnteredCostAndUpdatesLatestCatalogCost() {
        var inventory = mock(InventoryRepository.class); var receipts = mock(ReceivingRepository.class);
        when(inventory.lock(1L)).thenReturn(new InventoryRepository.LockedInventory(5,1,4,0,0));
        when(receipts.insert(1L,3,"Supplier",null,BigDecimal.valueOf(18000))).thenReturn(7L);
        new ReceivingService(inventory,receipts).receive(new ReceivingDtos.ReceivingRequest(1L,3," Supplier ",null,BigDecimal.valueOf(18000)));
        verify(inventory).updateQuantity(5,7);
        verify(receipts).insert(1L,3,"Supplier",null,BigDecimal.valueOf(18000));
        verify(receipts).updateLatestCost(1L,BigDecimal.valueOf(18000));
        verify(receipts).insertMovement(1L,3,7,null);
    }
    @Test void fractionalOrMissingCostCannotChangeStock() {
        var inventory = mock(InventoryRepository.class); var receipts = mock(ReceivingRepository.class);
        var service = new ReceivingService(inventory,receipts);
        assertThrows(IllegalArgumentException.class, () -> service.receive(new ReceivingDtos.ReceivingRequest(1L,3,null,null,null)));
        assertThrows(IllegalArgumentException.class, () -> service.receive(new ReceivingDtos.ReceivingRequest(1L,3,null,null,new BigDecimal("1.50"))));
        verifyNoInteractions(inventory,receipts);
    }
}
