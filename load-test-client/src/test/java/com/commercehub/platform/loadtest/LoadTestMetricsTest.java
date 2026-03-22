package com.commercehub.platform.loadtest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoadTestMetricsTest {

    @Test
    void computesPhaseAveragesFromSuccessfulRequests() {
        LoadTestMetrics metrics = new LoadTestMetrics(10, 2, 5000, 100, 50, 300);

        assertEquals(10.0, metrics.averageCreateCartMillis());
        assertEquals(5.0, metrics.averageAddItemMillis());
        assertEquals(30.0, metrics.averageCheckoutMillis());
        assertEquals(45.0, metrics.successfulScenarioAverageMillis());
    }
}
