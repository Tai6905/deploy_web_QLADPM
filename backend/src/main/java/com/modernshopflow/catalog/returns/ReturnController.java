package com.modernshopflow.catalog.returns;

import com.modernshopflow.catalog.returns.ReturnDtos.CreateReturnRequest;
import com.modernshopflow.catalog.returns.ReturnDtos.ReturnResponse;
import com.modernshopflow.catalog.returns.ReturnDtos.ReturnableOrder;
import com.modernshopflow.catalog.returns.ReturnDtos.UpdateReturnRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Role-aware HTTP boundary; owner decisions and warehouse restock are separated. */
@RestController
@RequestMapping("/returns")
public class ReturnController {
    private final ReturnService service;

    public ReturnController(ReturnService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReturnResponse> list(@RequestParam(defaultValue = "50") int limit) {
        return service.list(limit);
    }

    @GetMapping("/orders")
    public List<ReturnableOrder> listEligibleOrders() {
        return service.listReturnableOrders();
    }

    @PostMapping
    public ResponseEntity<ReturnResponse> create(@Valid @RequestBody CreateReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/{returnId}")
    public ReturnResponse update(
            @PathVariable long returnId,
            @RequestBody UpdateReturnRequest request,
            Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).collect(Collectors.toSet());
        return service.update(returnId, request.toStatus(), request.restockable(), roles);
    }
}