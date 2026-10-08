package com.modernshopflow.catalog.payments;

import com.modernshopflow.catalog.domain.CatalogConflictException;
import com.modernshopflow.catalog.domain.CatalogNotFoundException;
import com.modernshopflow.catalog.payments.PaymentDtos.PaymentRequest;
import com.modernshopflow.catalog.payments.PaymentDtos.PaymentResponse;
import com.modernshopflow.catalog.payments.PaymentRepository.LockedOrder;
import com.modernshopflow.catalog.payments.PaymentRepository.StockHold;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Simulates a single CARD payment attempt while atomically settling or releasing stock holds. */
@Service
public class PaymentService {
    private final PaymentRepository repository;

    public PaymentService(PaymentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PaymentResponse process(UUID orderRef, PaymentRequest request) {
        String result = request.result().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("SUCCESS", "FAILED", "EXPIRED").contains(result)) {
            throw new IllegalArgumentException("result must be SUCCESS, FAILED, or EXPIRED");
        }
        if (!"SUCCESS".equals(result)
                && (request.failureReason() == null || request.failureReason().isBlank())) {
            throw new IllegalArgumentException("failureReason is required for FAILED or EXPIRED");
        }

        LockedOrder order;
        try {
            order = repository.lockOrder(orderRef);
        } catch (EmptyResultDataAccessException exception) {
            throw new CatalogNotFoundException("Order was not found");
        }
        PaymentResponse attempt;
        try {
            attempt = repository.lockPayment(order.id());
        } catch (EmptyResultDataAccessException exception) {
            throw new CatalogNotFoundException("Payment attempt was not found");
        }
        if (!"PENDING_PAYMENT".equals(order.status()) || !"PENDING".equals(attempt.status())) {
            throw new CatalogConflictException("Order is not awaiting payment");
        }

        List<StockHold> holds = repository.lockActiveHolds(order.id());
        boolean expired = holds.isEmpty()
                || holds.stream().anyMatch(hold -> !hold.expiresAt().isAfter(OffsetDateTime.now()));
        if (expired) {
            repository.releaseHolds(holds, order.id(), "Checkout stock hold expired");
            repository.updatePayment(attempt.id(), "EXPIRED", "Checkout stock hold expired");
            repository.updateOrderStatus(order.id(), "PAYMENT_FAILED");
            return repository.getPayment(attempt.id());
        }

        if ("SUCCESS".equals(result)) {
            repository.commitHolds(holds, order.id());
            repository.updatePayment(attempt.id(), "SUCCESS", null);
            repository.updateOrderStatus(order.id(), "PAID");
        } else {
            repository.releaseHolds(holds, order.id(), request.failureReason().trim());
            repository.updatePayment(attempt.id(), result, request.failureReason().trim());
            repository.updateOrderStatus(order.id(), "PAYMENT_FAILED");
        }
        return repository.getPayment(attempt.id());
    }
}