package com.commercehub.platform.cart;

import com.commercehub.platform.shared.AuthorizeCardRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

@Component
public class HttpCreditCardAuthorizerClient implements CreditCardAuthorizerClient {
    private final RestClient restClient;
    private final String baseUrl;

    public HttpCreditCardAuthorizerClient(RestClient restClient,
                                          @Value("${services.credit-card-authorizer.base-url:http://localhost:8082}") String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    @Override
    public boolean authorize(String creditCardNumber) {
        try {
            restClient.post()
                    .uri(baseUrl + "/credit-card-authorizer/authorize")
                    .body(new AuthorizeCardRequest(creditCardNumber))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpStatusCodeException exception) {
            HttpStatusCode statusCode = exception.getStatusCode();
            if (statusCode.value() == 402) {
                return false;
            }
            throw exception;
        }
    }
}
