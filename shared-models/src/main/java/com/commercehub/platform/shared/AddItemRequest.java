package com.commercehub.platform.shared;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AddItemRequest(
        @Min(1) int productId,
        @Min(1) @Max(10_000) int quantity
) {
}
