package com.commercehub.platform.cca;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditCardAuthorizationServiceTest {

    @Mock
    private AuthorizationDecisionSource authorizationDecisionSource;

    @InjectMocks
    private CreditCardAuthorizationService creditCardAuthorizationService;

    @Test
    void authorizesWhenDecisionSourceAllowsIt() {
        when(authorizationDecisionSource.shouldAuthorize()).thenReturn(true);

        assertTrue(creditCardAuthorizationService.authorize("1234-5678-9012-3456"));
    }

    @Test
    void declinesWhenDecisionSourceRejectsIt() {
        when(authorizationDecisionSource.shouldAuthorize()).thenReturn(false);

        assertFalse(creditCardAuthorizationService.authorize("1234-5678-9012-3456"));
    }
}
