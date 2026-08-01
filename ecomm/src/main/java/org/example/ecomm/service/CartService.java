package org.example.ecomm.service;

import org.example.ecomm.pojo.Cart;
import org.example.ecomm.pojo.User;

import java.math.BigDecimal;

public interface CartService {
    Cart getCart(Long id);
    void clearCart(Long id);
    BigDecimal getTotalPrice(Long id);

    Cart initializeNewCart(User user);

    Cart getCartByUserId(Long userId);
}