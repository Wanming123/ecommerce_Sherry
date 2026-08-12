package org.example.orderservice.client;

import lombok.Data;

import java.util.List;

/**
 *  returned from GET /carts/user/{userId}/checkout-cart.
 */
@Data
public class CartCheckoutResponse {
    private Long cartId;
    private List<CartCheckoutItemResponse> items;
}
