package com.commercehub.platform.cart;

import com.commercehub.platform.shared.AddItemRequest;
import com.commercehub.platform.shared.CartItemDto;
import com.commercehub.platform.shared.CartView;
import com.commercehub.platform.shared.CheckoutResponse;
import com.commercehub.platform.shared.CreateCartRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
public class ShoppingCartService {
    private final ShoppingCartRepository shoppingCartRepository;
    private final CheckoutOrchestrator checkoutOrchestrator;
    private final Clock clock;
    private final long checkoutInProgressTimeoutMillis;

    public ShoppingCartService(ShoppingCartRepository shoppingCartRepository,
                               CheckoutOrchestrator checkoutOrchestrator,
                               Clock clock,
                               @Value("${cart.checkout.in-progress-timeout-ms:30000}") long checkoutInProgressTimeoutMillis) {
        this.shoppingCartRepository = shoppingCartRepository;
        this.checkoutOrchestrator = checkoutOrchestrator;
        this.clock = clock;
        this.checkoutInProgressTimeoutMillis = checkoutInProgressTimeoutMillis;
    }

    public ShoppingCart create(CreateCartRequest request) {
        return shoppingCartRepository.create(request.customerId());
    }

    public void addItem(String shoppingCartId, AddItemRequest request) {
        ShoppingCart cart = getRecoverableCart(shoppingCartId);
        ensureCartIsActive(cart);
        cart.addItem(new CartItemDto(request.productId(), request.quantity()));
        shoppingCartRepository.save(cart);
    }

    public CheckoutResponse checkout(String shoppingCartId, String creditCardNumber) {
        ShoppingCart cart = getRecoverableCart(shoppingCartId);
        ensureCartIsActive(cart);
        List<CartItemDto> items = cart.itemsView();

        if (items.isEmpty()) {
            throw new InvalidCartStateException("Shopping cart is empty");
        }

        cart.markCheckoutInProgress(clock.millis());
        shoppingCartRepository.save(cart);

        String orderId = shoppingCartRepository.nextOrderId();
        boolean success = checkoutOrchestrator.checkout(
                orderId,
                cart.shoppingCartId(),
                cart.customerId(),
                creditCardNumber,
                items
        );

        if (!success) {
            cart.markActive();
            shoppingCartRepository.save(cart);
            throw new CheckoutFailedException("Credit card authorization failed or warehouse publish was not confirmed");
        }

        cart.markCheckedOut();
        shoppingCartRepository.save(cart);
        return new CheckoutResponse(orderId);
    }

    public CartView get(String shoppingCartId) {
        return toView(getRecoverableCart(shoppingCartId));
    }

    private ShoppingCart getCart(String shoppingCartId) {
        return shoppingCartRepository.findById(shoppingCartId)
                .orElseThrow(() -> new ShoppingCartNotFoundException(shoppingCartId));
    }

    private ShoppingCart getRecoverableCart(String shoppingCartId) {
        ShoppingCart cart = getCart(shoppingCartId);
        if (isCheckoutTimedOut(cart)) {
            cart.markActive();
            try {
                return shoppingCartRepository.save(cart);
            } catch (ConcurrentCartModificationException exception) {
                return getCart(shoppingCartId);
            }
        }
        return cart;
    }

    private void ensureCartIsActive(ShoppingCart cart) {
        if (ShoppingCart.STATUS_CHECKOUT_IN_PROGRESS.equals(cart.status())) {
            throw new InvalidCartStateException("Shopping cart checkout is already in progress");
        }
        if (!ShoppingCart.STATUS_ACTIVE.equals(cart.status())) {
            throw new InvalidCartStateException("Shopping cart is not active");
        }
    }

    private CartView toView(ShoppingCart cart) {
        return new CartView(cart.shoppingCartId(), cart.customerId(), cart.status(), cart.items());
    }

    private boolean isCheckoutTimedOut(ShoppingCart cart) {
        if (!ShoppingCart.STATUS_CHECKOUT_IN_PROGRESS.equals(cart.status())) {
            return false;
        }
        Long startedAt = cart.checkoutStartedAtEpochMillis();
        return startedAt != null && clock.millis() - startedAt >= checkoutInProgressTimeoutMillis;
    }
}
