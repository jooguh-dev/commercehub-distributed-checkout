package com.commercehub.platform.cart;

import com.commercehub.platform.shared.CartItemDto;
import com.commercehub.platform.shared.CheckoutMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutOrchestratorTest {

    @Mock
    private CreditCardAuthorizerClient creditCardAuthorizerClient;

    @Mock
    private WarehousePublisher warehousePublisher;

    @InjectMocks
    private CheckoutOrchestrator checkoutOrchestrator;

    @Test
    void publishesOrderWhenCardIsAuthorized() {
        when(creditCardAuthorizerClient.authorize("1234-5678-9012-3456")).thenReturn(true);
        when(warehousePublisher.publish(org.mockito.ArgumentMatchers.any(CheckoutMessage.class))).thenReturn(true);

        boolean result = checkoutOrchestrator.checkout(
                "order-10",
                "cart-20",
                30,
                "1234-5678-9012-3456",
                List.of(new CartItemDto(1, 2))
        );

        ArgumentCaptor<CheckoutMessage> captor = ArgumentCaptor.forClass(CheckoutMessage.class);
        verify(warehousePublisher).publish(captor.capture());
        assertTrue(result);
        assertTrue(captor.getValue().items().stream().anyMatch(item -> item.productId() == 1 && item.quantity() == 2));
    }

    @Test
    void skipsPublishWhenCardIsDeclined() {
        when(creditCardAuthorizerClient.authorize("1234-5678-9012-3456")).thenReturn(false);

        boolean result = checkoutOrchestrator.checkout(
                "order-10",
                "cart-20",
                30,
                "1234-5678-9012-3456",
                List.of(new CartItemDto(1, 2))
        );

        assertFalse(result);
        verify(warehousePublisher, never()).publish(org.mockito.ArgumentMatchers.any(CheckoutMessage.class));
    }
}
