package com.commercehub.platform.cca;

import com.commercehub.platform.shared.AuthorizeCardRequest;
import com.commercehub.platform.shared.AuthorizeCardResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CreditCardAuthorizerController {
    private final CreditCardAuthorizationService creditCardAuthorizationService;

    public CreditCardAuthorizerController(CreditCardAuthorizationService creditCardAuthorizationService) {
        this.creditCardAuthorizationService = creditCardAuthorizationService;
    }

    @PostMapping("/credit-card-authorizer/authorize")
    public ResponseEntity<AuthorizeCardResponse> authorize(@Valid @RequestBody AuthorizeCardRequest request) {
        boolean authorized = creditCardAuthorizationService.authorize(request.creditCardNumber());
        if (authorized) {
            return ResponseEntity.ok(new AuthorizeCardResponse("AUTHORIZED"));
        }
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(new AuthorizeCardResponse("DECLINED"));
    }
}
