package org.example.orderservice.service;

import org.example.orderservice.dto.OrderDto;
import org.example.orderservice.pojo.Order;

import java.util.List;

public interface OrderService {
    Order placeOrder(Long userId, String authHeader);
    OrderDto getOrder(Long orderId);
    List<OrderDto> getUserOrders(Long userId);
    OrderDto convertToDto(Order order);
}
