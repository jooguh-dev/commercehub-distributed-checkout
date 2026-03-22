package com.commercehub.platform.shared;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CheckoutRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{4}$")
        String creditCardNumber
) {
}
