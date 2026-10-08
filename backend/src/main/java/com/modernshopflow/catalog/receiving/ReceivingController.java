package com.modernshopflow.catalog.receiving;

import com.modernshopflow.catalog.receiving.ReceivingDtos.ReceivingRequest;
import com.modernshopflow.catalog.receiving.ReceivingDtos.ReceivingResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API boundary for warehouse stock receiving records. */
@RestController
@RequestMapping("/receivings")
public class ReceivingController {
    private final ReceivingService service;

    public ReceivingController(ReceivingService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReceivingResponse> list(@RequestParam(defaultValue = "50") int limit) {
        return service.list(limit);
    }

    @PostMapping
    public ResponseEntity<ReceivingResponse> receive(@Valid @RequestBody ReceivingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.receive(request));
    }
}