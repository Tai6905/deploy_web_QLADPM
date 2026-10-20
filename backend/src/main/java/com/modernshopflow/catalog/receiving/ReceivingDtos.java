package com.modernshopflow.catalog.receiving;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

/** Request and response contracts for supplier stock receipts. */
public final class ReceivingDtos {
    private ReceivingDtos() {}

    public record ReceivingRequest(
            @NotNull @Positive Long variantId,
            @NotNull @Positive Integer quantity,
            @Size(max = 255) String supplierName,
            @Size(max = 500) String note,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 0) BigDecimal unitCost) {
        public ReceivingRequest(Long variantId, Integer quantity, String supplierName, String note) {
            this(variantId, quantity, supplierName, note, BigDecimal.ZERO);
        }
    }

    public record ReceivingResponse(
            long id,
            long productId,
            long variantId,
            String productName,
            int quantity,
            String supplierName,
            String note,
            OffsetDateTime createdAt,
            int onHandStock,
            int reservedStock,
            int availableStock,
            Long unitCost,
            Long totalCost) {}
}
