package org.example.orderservice.dto;

import org.example.orderservice.pojo.Order;
import org.example.orderservice.pojo.OrderItem;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderPlacedEvent(
        String eventType,
        Long orderId,
        Long userId,
        BigDecimal totalAmount,
        String orderStatus,
        Instant placedAt,
        List<OrderItemSnapshot> items
) implements Serializable {

    // resolve user service in down stream servies
    public static OrderPlacedEvent of(Order order) {
        List<OrderItemSnapshot> items = order.getOrderItems().stream()
                .map(OrderItemSnapshot::of)
                .toList();
        return new OrderPlacedEvent(
                "ORDER_PLACED",
                order.getOrderId(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getOrderStatus().name(),
                Instant.now(),
                items
        );
    }

    public record OrderItemSnapshot(Long productId, String productName, int quantity, BigDecimal price) implements Serializable {
        public static OrderItemSnapshot of(OrderItem item) {
            return new OrderItemSnapshot(item.getProductId(), item.getProductName(), item.getQuantity(), item.getPrice());
        }
    }
}
