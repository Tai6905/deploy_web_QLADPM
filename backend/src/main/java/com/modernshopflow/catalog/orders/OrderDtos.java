package com.modernshopflow.catalog.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Request and response records for guest checkout and owner order listing. */
public final class OrderDtos {
    private OrderDtos() {}

    public record Customer(
            @NotBlank @Size(max = 255) String fullName,
            @Email @Size(max = 255) String email,
            @Size(max = 20) String phone) {}

    public record ShippingAddress(
            @NotBlank @Size(max = 255) String receiverName,
            @NotBlank @Size(max = 20) @jakarta.validation.constraints.Pattern(regexp="\\+?[0-9][0-9 .()-]{7,19}",message="Số điện thoại không hợp lệ") String phone,
            @NotBlank @Size(max = 500) String addressLine,
            @Size(max = 100) String district,
            @NotBlank @Size(max = 100) String city) {}

    public record OrderLineRequest(
            @NotNull @Positive Long productId,
            @NotNull @Positive Integer quantity) {}

    public record CreateOrderRequest(
            @NotNull @Valid Customer customer,
            @NotNull @Valid ShippingAddress shippingAddress,
            @NotBlank String paymentMethod,
            @NotEmpty @Size(max=100) List<@NotNull @Valid OrderLineRequest> items,
            @Size(max=50) String voucherCode,
            UUID checkoutKey) {
        public CreateOrderRequest(Customer customer, ShippingAddress address, String method, List<OrderLineRequest> items) {
            this(customer,address,method,items,null,null);
        }
    }

    public record OrderLineResponse(
            Long productId,
            String productName,
            Long variantId,
            long unitPrice,
            int quantity,
            long lineTotal) {}

    public record OrderResponse(
            Long id,
            UUID orderRef,
            String status,
            String deliveryStatus,
            String paymentMethod,
            long totalAmount,
            Customer customer,
            ShippingAddress shippingAddress,
            List<OrderLineResponse> items,
            OffsetDateTime createdAt,
            long subtotalAmount, long discountAmount, String voucherCode, CancellationInfo cancellation) {
        public OrderResponse(Long id, UUID orderRef, String status, String deliveryStatus, String paymentMethod, long totalAmount,
                Customer customer, ShippingAddress shippingAddress, List<OrderLineResponse> items, OffsetDateTime createdAt,
                long subtotalAmount, long discountAmount, String voucherCode) {
            this(id,orderRef,status,deliveryStatus,paymentMethod,totalAmount,customer,shippingAddress,items,createdAt,subtotalAmount,discountAmount,voucherCode,null);
        }
    }

    public record CancelOrderRequest(@NotBlank @Size(max=500) String reason) {}
    public record CancellationInfo(String reason, OffsetDateTime cancelledAt, String actorKind,
            String paymentOutcome, long refundAmount) {}

    public record OrderSummary(
            Long id,
            UUID orderRef,
            String customer,
            String product,
            int quantity,
            long totalAmount,
            String status,
            String paymentStatus,
            String deliveryStatus,
            OffsetDateTime createdAt,
            String paymentMethod, CancellationInfo cancellation) {
        public OrderSummary(Long id, UUID orderRef, String customer, String product, int quantity, long totalAmount,
                String status, String paymentStatus, String deliveryStatus, OffsetDateTime createdAt, String paymentMethod) {
            this(id,orderRef,customer,product,quantity,totalAmount,status,paymentStatus,deliveryStatus,createdAt,paymentMethod,null);
        }
        public OrderSummary withCancellation(CancellationInfo info) {
            return new OrderSummary(id,orderRef,customer,product,quantity,totalAmount,status,paymentStatus,deliveryStatus,createdAt,paymentMethod,info);
        }
    }
}
