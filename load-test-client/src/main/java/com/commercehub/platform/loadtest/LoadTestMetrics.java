package com.commercehub.platform.loadtest;

public record LoadTestMetrics(long successfulRequests,
                              long unsuccessfulRequests,
                              long wallTimeMillis,
                              long createCartTimeMillis,
                              long addItemTimeMillis,
                              long checkoutTimeMillis) {

    public long totalRequests() {
        return successfulRequests + unsuccessfulRequests;
    }

    public double throughputPerSecond() {
        if (wallTimeMillis <= 0) {
            return 0.0;
        }
        return totalRequests() * 1000.0 / wallTimeMillis;
    }

    public double successRate() {
        if (totalRequests() == 0) {
            return 0.0;
        }
        return successfulRequests * 1.0 / totalRequests();
    }

    public double averageCreateCartMillis() {
        return averagePerRequest(createCartTimeMillis);
    }

    public double averageAddItemMillis() {
        return averagePerRequest(addItemTimeMillis);
    }

    public double averageCheckoutMillis() {
        return averagePerRequest(checkoutTimeMillis);
    }

    public double successfulScenarioAverageMillis() {
        if (successfulRequests <= 0) {
            return 0.0;
        }
        return (createCartTimeMillis + addItemTimeMillis + checkoutTimeMillis) * 1.0 / successfulRequests;
    }

    private double averagePerRequest(long phaseTimeMillis) {
        if (successfulRequests <= 0) {
            return 0.0;
        }
        return phaseTimeMillis * 1.0 / successfulRequests;
    }
}
