package com.commercehub.platform.product;

import com.commercehub.platform.shared.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProductExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ProductNotFoundException exception) {
        return new ErrorResponse("PRODUCT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ProductTemporarilyUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleTemporarilyUnavailable(ProductTemporarilyUnavailableException exception) {
        return new ErrorResponse("PRODUCT_TEMPORARILY_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        return new ErrorResponse("INVALID_INPUT", exception.getBindingResult().getAllErrors().get(0).getDefaultMessage());
    }
}
