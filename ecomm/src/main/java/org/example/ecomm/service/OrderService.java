package org.example.ecomm.service;

import org.example.ecomm.dto.OrderDto;
import org.example.ecomm.pojo.Order;

import java.util.List;

public interface OrderService {
    Order placeOrder(Long userId);
    OrderDto getOrder(Long orderId);
    List<OrderDto> getUserOrders(Long userId);
    OrderDto convertToDto(Order order);
}
