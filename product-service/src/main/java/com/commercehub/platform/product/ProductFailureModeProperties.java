package com.commercehub.platform.product;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "product.bad-instance")
public record ProductFailureModeProperties(
        boolean enabled
) {
}
