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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ModelMapper modelMapper;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private final EcommClient ecommClient;
    @Qualifier("inventoryReservationExecutor")
    private final Executor inventoryReservationExecutor;

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

            kafkaTemplate.send("order-placed", savedOrder.getOrderId().toString(), OrderPlacedEvent.of(savedOrder));
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

    // Reserves inventory for every cart item in parallel on a bounded pool, since each call is
    // a network round-trip to the ecomm service. Concurrent calls can finish in any order, so
    // `reservedItems` can no longer be assumed to be "the prefix that succeeded" (as it was when
    // this ran sequentially) - it is instead built from whichever futures actually completed
    // successfully, which is still exactly the set that needs compensating on partial failure.
    // All futures are awaited even after the first failure, since calls already in flight can't
    // be cancelled mid-RPC and every successful one still needs to be recorded for rollback.
    private List<OrderItem> reserveInventoryAndBuildOrderItems(Order order, CartCheckoutResponse cart,
                                                                List<CartCheckoutItemResponse> reservedItems) {
        List<CartCheckoutItemResponse> items = cart.getItems();
        List<CompletableFuture<CartCheckoutItemResponse>> futures = items.stream()
                .map(item -> CompletableFuture.supplyAsync(() -> {
                    ecommClient.decreaseInventory(item.getProductId(), item.getQuantity());
                    return item;
                }, inventoryReservationExecutor))
                .collect(Collectors.toList());

        RuntimeException firstFailure = null;
        for (CompletableFuture<CartCheckoutItemResponse> future : futures) {
            try {
                reservedItems.add(future.join());
            } catch (CompletionException ex) {
                if (firstFailure == null) {
                    Throwable cause = ex.getCause();
                    firstFailure = cause instanceof RuntimeException runtimeCause
                            ? runtimeCause
                            : new OrderPlacementFailedException("Inventory reservation failed", cause);
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }

        return items.stream()
                .map(item -> new OrderItem(order, item.getProductId(), item.getProductName(), item.getQuantity(), item.getUnitPrice()))
                .collect(Collectors.toList());
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
