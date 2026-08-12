package org.example.ecomm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Purpose-built response for order-service's remote call when placing an order -
 * a flat snapshot of the cart, not the JPA entity graph.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartCheckoutDto {
    private Long cartId;
    private List<CartCheckoutItemDto> items;
}
