package org.example.orderservice.exception;

public class OrderPlacementFailedException extends RuntimeException {
    public OrderPlacementFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
