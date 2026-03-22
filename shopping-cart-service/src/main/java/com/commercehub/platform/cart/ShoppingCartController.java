package com.commercehub.platform.cart;

import com.commercehub.platform.shared.AddItemRequest;
import com.commercehub.platform.shared.CartView;
import com.commercehub.platform.shared.CheckoutRequest;
import com.commercehub.platform.shared.CheckoutResponse;
import com.commercehub.platform.shared.CreateCartRequest;
import com.commercehub.platform.shared.CreateCartResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShoppingCartController {
    private final ShoppingCartService shoppingCartService;

    public ShoppingCartController(ShoppingCartService shoppingCartService) {
        this.shoppingCartService = shoppingCartService;
    }

    @PostMapping("/shopping-cart")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateCartResponse createCart(@Valid @RequestBody CreateCartRequest request) {
        return new CreateCartResponse(shoppingCartService.create(request).shoppingCartId());
    }

    @PostMapping("/shopping-carts/{shoppingCartId}/addItem")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addItem(@PathVariable("shoppingCartId") String shoppingCartId, @Valid @RequestBody AddItemRequest request) {
        shoppingCartService.addItem(shoppingCartId, request);
    }

    @PostMapping("/shopping-carts/{shoppingCartId}/checkout")
    public CheckoutResponse checkout(@PathVariable("shoppingCartId") String shoppingCartId, @Valid @RequestBody CheckoutRequest request) {
        return shoppingCartService.checkout(shoppingCartId, request.creditCardNumber());
    }

    @GetMapping("/shopping-carts/{shoppingCartId}")
    public CartView getCart(@PathVariable("shoppingCartId") String shoppingCartId) {
        return shoppingCartService.get(shoppingCartId);
    }
}
