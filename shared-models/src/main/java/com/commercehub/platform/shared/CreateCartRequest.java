package com.commercehub.platform.shared;

import jakarta.validation.constraints.Min;

public record CreateCartRequest(@Min(1) int customerId) {
}
