package com.commercehub.platform.shared;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ProductDto(
        @Min(1) int productId,
        @NotBlank String sku,
        @NotBlank String manufacturer,
        @Min(1) int categoryId,
        @Min(0) int weight,
        @Min(1) int someOtherId
) {
}
