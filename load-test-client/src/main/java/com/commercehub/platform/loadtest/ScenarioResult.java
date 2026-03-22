package com.commercehub.platform.loadtest;

public record ScenarioResult(int statusCode,
                             long createCartTimeMillis,
                             long addItemTimeMillis,
                             long checkoutTimeMillis) {
}
