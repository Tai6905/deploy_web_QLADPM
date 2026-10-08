package com.modernshopflow.catalog.payments;

import com.modernshopflow.catalog.payments.PaymentDtos.PaymentRequest;
import com.modernshopflow.catalog.payments.PaymentDtos.PaymentResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public demo payment route uses the unguessable orderRef capability instead of a sequence id. */
@RestController
@RequestMapping("/orders/{orderRef}/payments")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    public PaymentResponse simulate(
            @PathVariable UUID orderRef,
            @Valid @RequestBody PaymentRequest request) {
        return service.process(orderRef, request);
    }
}