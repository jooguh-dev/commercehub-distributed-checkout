package com.commercehub.platform.cart;

import com.commercehub.platform.shared.AddItemRequest;
import com.commercehub.platform.shared.CreateCartRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShoppingCartServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-22T04:00:00Z"), ZoneOffset.UTC);


    @Test
    void createsCartAndAddsItems() {
        ShoppingCartService service = new ShoppingCartService(
                new InMemoryShoppingCartRepository(),
                new CheckoutOrchestrator(card -> true, message -> true),
                FIXED_CLOCK,
                30_000
        );

        String cartId = service.create(new CreateCartRequest(99)).shoppingCartId();
        service.addItem(cartId, new AddItemRequest(1, 3));

        assertEquals(1, service.get(cartId).items().size());
        assertEquals(99, service.get(cartId).customerId());
    }

    @Test
    void failsCheckoutWhenCartEmpty() {
        ShoppingCartService service = new ShoppingCartService(
                new InMemoryShoppingCartRepository(),
                new CheckoutOrchestrator(card -> true, message -> true),
                FIXED_CLOCK,
                30_000
        );

        String cartId = service.create(new CreateCartRequest(99)).shoppingCartId();

        assertThrows(InvalidCartStateException.class, () -> service.checkout(cartId, "1234-5678-9012-3456"));
    }

    @Test
    void resetsCartToActiveWhenCheckoutFails() {
        InMemoryShoppingCartRepository repository = new InMemoryShoppingCartRepository();
        ShoppingCartService service = new ShoppingCartService(
                repository,
                new CheckoutOrchestrator(card -> true, message -> false),
                FIXED_CLOCK,
                30_000
        );

        String cartId = service.create(new CreateCartRequest(99)).shoppingCartId();
        service.addItem(cartId, new AddItemRequest(1, 3));

        assertThrows(CheckoutFailedException.class, () -> service.checkout(cartId, "1234-5678-9012-3456"));
        assertEquals(ShoppingCart.STATUS_ACTIVE, repository.findById(cartId).orElseThrow().status());
    }

    @Test
    void marksCartCheckedOutWhenCheckoutSucceeds() {
        InMemoryShoppingCartRepository repository = new InMemoryShoppingCartRepository();
        ShoppingCartService service = new ShoppingCartService(
                repository,
                new CheckoutOrchestrator(card -> true, message -> true),
                FIXED_CLOCK,
                30_000
        );

        String cartId = service.create(new CreateCartRequest(99)).shoppingCartId();
        service.addItem(cartId, new AddItemRequest(1, 3));
        service.checkout(cartId, "1234-5678-9012-3456");

        assertEquals(ShoppingCart.STATUS_CHECKED_OUT, repository.findById(cartId).orElseThrow().status());
    }

    @Test
    void recoversTimedOutCheckoutInProgressCart() {
        InMemoryShoppingCartRepository repository = new InMemoryShoppingCartRepository();
        ShoppingCart cart = repository.create(99);
        cart.markCheckoutInProgress(FIXED_CLOCK.millis() - 31_000);
        repository.save(cart);

        ShoppingCartService service = new ShoppingCartService(
                repository,
                new CheckoutOrchestrator(card -> true, message -> true),
                FIXED_CLOCK,
                30_000
        );

        assertEquals(ShoppingCart.STATUS_ACTIVE, service.get(cart.shoppingCartId()).status());
    }
}
