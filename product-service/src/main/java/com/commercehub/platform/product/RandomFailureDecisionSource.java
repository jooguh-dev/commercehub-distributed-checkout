package com.commercehub.platform.product;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class RandomFailureDecisionSource implements FailureDecisionSource {
    @Override
    public boolean shouldFail() {
        return ThreadLocalRandom.current().nextBoolean();
    }
}
