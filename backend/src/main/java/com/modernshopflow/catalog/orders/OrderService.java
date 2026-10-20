package com.modernshopflow.catalog.orders;

import com.modernshopflow.catalog.domain.PageResult;
import com.modernshopflow.catalog.orders.OrderDtos.CreateOrderRequest;
import com.modernshopflow.catalog.orders.OrderDtos.OrderResponse;
import com.modernshopflow.catalog.orders.OrderDtos.OrderSummary;
import com.modernshopflow.catalog.orders.OrderRepository.VariantAvailability;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates atomic checkout, stock holds, payment initialization, and owner order reads. */
@Service
public class OrderService {
    private static final long MAX_ORDER_TOTAL = 9_999_999_999L;
    private static final int HOLD_MINUTES = 15;

    private final OrderRepository repository;
    private final com.modernshopflow.catalog.checkout.VoucherService vouchers;
    private final com.modernshopflow.catalog.payments.PaymentRepository payments;

    public OrderService(OrderRepository repository,com.modernshopflow.catalog.checkout.VoucherService vouchers,com.modernshopflow.catalog.payments.PaymentRepository payments) {
        this.repository = repository;
        this.vouchers=vouchers;this.payments=payments;
    }

    /** Creates all order rows and stock holds together or rolls the complete operation back. */
    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        return create(request, null);
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, Long customerId) {
        if (!Set.of("CARD","BANK_TRANSFER","COD").contains(request.paymentMethod())) {
            throw new IllegalArgumentException("Phương thức thanh toán không được hỗ trợ.");
        }
        String fingerprint=fingerprint(request);
        if(customerId!=null) repository.lockCustomer(customerId);
        if(request.checkoutKey()!=null) {
            if(customerId==null) throw new IllegalArgumentException("Đăng nhập để đặt hàng.");
            var existing=repository.checkout(request.checkoutKey(),customerId,fingerprint);
            if(existing.isPresent()) return existing.get();
        }
        Set<Long> productIds = new HashSet<>();
        List<Allocation> allocations = new ArrayList<>();
        long total = 0;

        for (var line : request.items().stream().sorted(java.util.Comparator.comparing(OrderDtos.OrderLineRequest::productId)).toList()) {
            if (!productIds.add(line.productId())) {
                throw new IllegalArgumentException("Duplicate productId in items");
            }
            if (!repository.productIsSellable(line.productId())) {
                throw new IllegalArgumentException("Product is not available: " + line.productId());
            }

            int remaining = line.quantity();
            List<VariantAvailability> variants = repository.lockAvailableVariants(line.productId());
            for (VariantAvailability variant : variants) {
                if (remaining == 0) break;
                int allocated = Math.min(remaining, variant.availableQuantity());
                if (allocated <= 0) continue;
                allocations.add(new Allocation(line.productId(), variant, allocated));
                remaining -= allocated;
                total = Math.addExact(total, Math.multiplyExact(variant.unitPrice(), allocated));
            }
            if (remaining > 0) {
                throw new IllegalArgumentException(
                        "Insufficient stock for product " + line.productId() + "; unavailable quantity: " + remaining);
            }
        }

        if (total > MAX_ORDER_TOTAL) {
            throw new IllegalArgumentException("Order total exceeds supported maximum");
        }

        UUID orderRef = UUID.randomUUID();
        var price=vouchers.price(total,request.voucherCode(),customerId,true);
        long orderId = repository.insertOrder(orderRef, request, price.totalAmount(), customerId);
        repository.checkoutPrice(orderId,request,price,fingerprint);
        OffsetDateTime holdExpiresAt = OffsetDateTime.now().plusMinutes(HOLD_MINUTES);
        for (Allocation allocation : allocations) {
            VariantAvailability variant = allocation.variant();
            repository.insertOrderItem(orderId, allocation.productId(), variant.variantId(),
                    variant.productName(), variant.unitPrice(), allocation.quantity());
            repository.addStockHold(variant.inventoryItemId(), orderId, allocation.quantity(), holdExpiresAt);
            repository.addMovement(variant.variantId(), "ORDER_RESERVED", allocation.quantity(), orderId,
                    "Checkout stock hold");
        }
        repository.insertPaymentAttempt(orderId, price.totalAmount());
        vouchers.record(price,orderId,customerId);
        if("COD".equals(request.paymentMethod())) payments.commitHolds(payments.lockActiveHolds(orderId),orderId);
        return repository.findOrder(orderId);
    }

    /** Returns recent orders for the owner workspace using zero-based API pagination. */
    public PageResult<OrderSummary> list(int page, int size) {
        return list(page, size, null);
    }

    public PageResult<OrderSummary> list(int page, int size, String search) {
        int offset = pageOffset(page, size);
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return new PageResult<>(repository.listOrders(size, offset, normalizedSearch), repository.countOrders(normalizedSearch));
    }

    public OrderResponse getByReference(UUID orderRef) {
        return repository.findOrderByRef(orderRef);
    }

    public PageResult<OrderSummary> listMine(long customerId, int page, int size, String search) {
        int offset = pageOffset(page, size);
        String query = search == null || search.isBlank() ? null : search.trim();
        return new PageResult<>(repository.listOrders(size, offset, query, customerId), repository.countOrders(query, customerId));
    }
    public OrderResponse getMine(long id, long customerId) { return repository.findOwnedOrder(id, customerId); }
    public OrderResponse getMineByReference(UUID reference, long customerId) { return repository.findOwnedOrderByRef(reference, customerId); }

    private int pageOffset(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        }
        try {
            return Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("page is too large for the requested size", exception);
        }
    }

    private record Allocation(long productId, VariantAvailability variant, int quantity) {}

    private String fingerprint(CreateOrderRequest request) {
        try {
            StringBuilder value=new StringBuilder();
            for(String field:new String[]{request.customer().fullName(),request.customer().email(),request.customer().phone(),request.shippingAddress().receiverName(),request.shippingAddress().phone(),request.shippingAddress().addressLine(),request.shippingAddress().district(),request.shippingAddress().city(),request.paymentMethod(),request.voucherCode()}) {
                String normalized=field==null?"":field.trim();value.append(normalized.length()).append(':').append(normalized);
            }
            request.items().stream().sorted(java.util.Comparator.comparing(OrderDtos.OrderLineRequest::productId)).forEach(line->value.append('|').append(line.productId()).append(':').append(line.quantity()));
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch(java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
