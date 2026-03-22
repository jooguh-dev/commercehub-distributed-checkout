package com.commercehub.platform.cart;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
public class ShoppingCartConfiguration {

    @Bean
    public CheckoutOrchestrator checkoutOrchestrator(CreditCardAuthorizerClient creditCardAuthorizerClient,
                                                     WarehousePublisher warehousePublisher) {
        return new CheckoutOrchestrator(creditCardAuthorizerClient, warehousePublisher);
    }

    @Bean
    public RestClient restClient(RestClient.Builder builder,
                                 @Value("${http.client.pool.max-total-connections:200}") int maxTotalConnections,
                                 @Value("${http.client.pool.max-connections-per-route:100}") int maxConnectionsPerRoute,
                                 @Value("${http.client.connect-timeout-ms:3000}") int connectTimeoutMillis,
                                 @Value("${http.client.read-timeout-ms:5000}") int readTimeoutMillis,
                                 @Value("${http.client.connection-request-timeout-ms:2000}") int connectionRequestTimeoutMillis) {
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(maxTotalConnections)
                .setMaxConnPerRoute(maxConnectionsPerRoute)
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofSeconds(30))
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout(connectTimeoutMillis);
        requestFactory.setReadTimeout(readTimeoutMillis);
        requestFactory.setConnectionRequestTimeout(connectionRequestTimeoutMillis);

        return builder.requestFactory(requestFactory).build();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
