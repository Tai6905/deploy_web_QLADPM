package com.modernshopflow.catalog.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductCreateRequest(
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 100) String sku,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 5000) String description,
        @DecimalMin("0") @Digits(integer = 10, fraction = 0) BigDecimal costPrice,
        @NotNull @DecimalMin("1") @Digits(integer = 10, fraction = 0) BigDecimal listPrice) {}
