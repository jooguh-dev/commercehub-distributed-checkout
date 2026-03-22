package com.commercehub.platform.cca;

import org.springframework.stereotype.Service;

@Service
public class CreditCardAuthorizationService {
    private final AuthorizationDecisionSource authorizationDecisionSource;

    public CreditCardAuthorizationService(AuthorizationDecisionSource authorizationDecisionSource) {
        this.authorizationDecisionSource = authorizationDecisionSource;
    }

    public boolean authorize(String creditCardNumber) {
        return authorizationDecisionSource.shouldAuthorize();
    }
}
