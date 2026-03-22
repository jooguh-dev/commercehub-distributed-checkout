package com.commercehub.platform.cart;

import com.commercehub.platform.shared.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ShoppingCartExceptionHandler {

    @ExceptionHandler(ShoppingCartNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ShoppingCartNotFoundException exception) {
        return new ErrorResponse("SHOPPING_CART_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ConcurrentCartModificationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(ConcurrentCartModificationException exception) {
        return new ErrorResponse("CONCURRENT_CART_MODIFICATION", exception.getMessage());
    }

    @ExceptionHandler({InvalidCartStateException.class, CheckoutFailedException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(RuntimeException exception) {
        return new ErrorResponse("INVALID_CART_STATE", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        return new ErrorResponse("INVALID_INPUT", exception.getBindingResult().getAllErrors().get(0).getDefaultMessage());
    }
}
