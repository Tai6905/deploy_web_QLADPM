package com.modernshopflow.catalog.api;
import jakarta.validation.constraints.*;
import java.util.List;
public record ProductContentRequest(@Size(max=5000) String description, @Size(max=5000) String usageInstructions,
        @NotNull @Size(max=8) List<@NotBlank @Size(max=500) String> imageUrls,
        @Size(max=5) java.util.Map<String, String> specifications) {
    public ProductContentRequest(String description, String usageInstructions, List<String> imageUrls) {
        this(description, usageInstructions, imageUrls, null);
    }
}
