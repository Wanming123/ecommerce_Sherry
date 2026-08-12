package org.example.orderservice.client;

import lombok.Data;

/**
 * Mirrors ecomm's {message, data} ApiResponse shape so RestTemplate can deserialize the "data" payload with type information.
 */
@Data
public class ApiResponseWrapper<T> {
    private String message;
    private T data;
}
