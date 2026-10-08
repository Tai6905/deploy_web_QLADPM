package com.modernshopflow.catalog.payments;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** Request/response records for the local payment simulator. */
public final class PaymentDtos {
    private PaymentDtos() {}

    public record PaymentRequest(@NotBlank String result, @Size(max = 500) String failureReason) {}

    public record PaymentResponse(
            Long id,
            Long orderId,
            String method,
            String status,
            long amount,
            OffsetDateTime paidAt,
            String failedReason,
            OffsetDateTime createdAt,
            String orderStatus) {}
}