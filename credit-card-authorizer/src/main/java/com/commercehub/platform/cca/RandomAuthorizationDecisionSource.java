package com.commercehub.platform.cca;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class RandomAuthorizationDecisionSource implements AuthorizationDecisionSource {

    @Override
    public boolean shouldAuthorize() {
        return ThreadLocalRandom.current().nextInt(10) < 9;
    }
}
