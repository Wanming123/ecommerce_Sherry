package org.example.orderservice.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.orderservice.Enums.OrderStatus;
import org.example.orderservice.client.CartCheckoutItemResponse;
import org.example.orderservice.client.CartCheckoutResponse;
import org.example.orderservice.client.EcommClient;
import org.example.orderservice.dto.OrderDto;
import org.example.orderservice.dto.OrderPlacedEvent;
import org.example.orderservice.exception.OrderPlacementFailedException;
import org.example.orderservice.exception.ResourceNotFoundException;
import org.example.orderservice.pojo.Order;
import org.example.orderservice.pojo.OrderItem;
import org.example.orderservice.repository.OrderRepository;
import org.modelmapper.ModelMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ModelMapper modelMapper;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private final EcommClient ecommClient;

    @Transactional
    @Override
    public Order placeOrder(Long userId, String authHeader) {
        CartCheckoutResponse cart = fetchCheckoutCart(userId, authHeader);
        Order order = createOrder(userId);

        List<CartCheckoutItemResponse> reservedItems = new ArrayList<>();
        try {
            List<OrderItem> orderItemList = reserveInventoryAndBuildOrderItems(order, cart, reservedItems);
            order.setOrderItems(new HashSet<>(orderItemList));
            order.setTotalAmount(calculateTotalAmount(orderItemList));
            order.setOrderStatus(OrderStatus.CONFIRMED);
            Order savedOrder = orderRepository.save(order);

            clearCartBestEffort(cart.getCartId(), authHeader);

            kafkaTemplate.send("order-placed", savedOrder.getOrderId().toString(),
                    new OrderPlacedEvent(savedOrder.getOrderId(), userId, savedOrder.getTotalAmount()));
            return savedOrder;
        } catch (Exception ex) {
            compensateInventory(reservedItems);
            throw new OrderPlacementFailedException("Order placement failed and was rolled back", ex);
        }
    }

    private CartCheckoutResponse fetchCheckoutCart(Long userId, String authHeader) {
        CartCheckoutResponse cart = ecommClient.fetchCheckoutCart(userId, authHeader);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart not found for user " + userId);
        }
        return cart;
    }

    private Order createOrder(Long userId) {
        Order order = new Order();
        order.setUserId(userId);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setOrderDate(LocalDate.now());
        return order;
    }

    // Reserves inventory sequentially (one item at a time) rather than in parallel so that,
    // on partial failure, `reservedItems` is exactly the prefix that succeeded - this is what
    // makes precise compensation possible.
    private List<OrderItem> reserveInventoryAndBuildOrderItems(Order order, CartCheckoutResponse cart,
                                                                List<CartCheckoutItemResponse> reservedItems) {
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartCheckoutItemResponse item : cart.getItems()) {
            ecommClient.decreaseInventory(item.getProductId(), item.getQuantity());
            reservedItems.add(item);
            orderItems.add(new OrderItem(order, item.getProductId(), item.getProductName(), item.getQuantity(), item.getUnitPrice()));
        }
        return orderItems;
    }

    // Compensating step of the saga: undo inventory reservations already confirmed
    // successful. One failed restore must not stop the rest from being attempted.
    private void compensateInventory(List<CartCheckoutItemResponse> reservedItems) {
        for (int i = reservedItems.size() - 1; i >= 0; i--) {
            CartCheckoutItemResponse item = reservedItems.get(i);
            try {
                ecommClient.restoreInventory(item.getProductId(), item.getQuantity());
            } catch (Exception compEx) {
                log.error("Compensation call itself failed for productId={}, quantity={}",
                        item.getProductId(), item.getQuantity(), compEx);
            }
        }
    }

    // Cart-clear is not saga-critical: a stale cart after a confirmed order is a UX
    // nit, not worth rolling back inventory/order over.
    private void clearCartBestEffort(Long cartId, String authHeader) {
        try {
            ecommClient.clearCart(cartId, authHeader);
        } catch (Exception e) {
            log.warn("Cart clear failed after order was confirmed; cart {} left stale", cartId, e);
        }
    }

    private BigDecimal calculateTotalAmount(List<OrderItem> orderItemList) {
        return orderItemList
                .stream()
                .map(item -> item.getPrice()
                        .multiply(new BigDecimal(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public OrderDto getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .map(this::convertToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    @Override
    public List<OrderDto> getUserOrders(Long userId) {
        List<Order> orders = orderRepository.findByUserId(userId);
        return orders.stream().map(this::convertToDto).toList();
    }

    @Override
    public OrderDto convertToDto(Order order) {
        return modelMapper.map(order, OrderDto.class);
    }
}
