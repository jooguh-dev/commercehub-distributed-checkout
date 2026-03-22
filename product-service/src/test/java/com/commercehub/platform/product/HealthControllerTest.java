package com.commercehub.platform.product;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthControllerTest {

    @Test
    void returnsServiceHealth() {
        HealthController controller = new HealthController();

        Map<String, String> response = controller.health();

        assertEquals("UP", response.get("status"));
        assertEquals("product-service", response.get("service"));
    }
}
