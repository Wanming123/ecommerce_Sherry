package org.example.ecomm.service;

import org.example.ecomm.dto.CartCheckoutDto;
import org.example.ecomm.pojo.Cart;

import java.math.BigDecimal;

public interface CartService {
    Cart getCart(Long id);
    void clearCart(Long id);
    BigDecimal getTotalPrice(Long id);

    Cart initializeNewCart(Long userId);

    Cart getCartByUserId(Long userId);

    CartCheckoutDto getCheckoutCart(Long userId);
}