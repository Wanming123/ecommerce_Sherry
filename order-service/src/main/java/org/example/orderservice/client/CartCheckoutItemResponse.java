package org.example.orderservice.client;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartCheckoutItemResponse {
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
}
