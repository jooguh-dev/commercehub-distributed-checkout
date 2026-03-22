package com.commercehub.platform.warehouse;

public class InvalidWarehouseMessageException extends RuntimeException {
    public InvalidWarehouseMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
