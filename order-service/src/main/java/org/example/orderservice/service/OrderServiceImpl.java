package org.example.orderservice.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.orderservice.Enums.OrderStatus;
import org.example.orderservice.client.ApiResponseWrapper;
import org.example.orderservice.client.CartCheckoutResponse;
import org.example.orderservice.dto.OrderDto;
import org.example.orderservice.dto.OrderPlacedEvent;
import org.example.orderservice.exception.ResourceNotFoundException;
import org.example.orderservice.pojo.Order;
import org.example.orderservice.pojo.OrderItem;
import org.example.orderservice.repository.OrderRepository;
import org.modelmapper.ModelMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private static final String ECOMM_BASE_URL = "http://ecomm/api/v1";

    private final OrderRepository orderRepository;
    private final ModelMapper modelMapper;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private final RestTemplate restTemplate;
    private final ExecutorService inventoryUpdateExecutor;

    @Transactional
    @Override
    public Order placeOrder(Long userId) {
        CartCheckoutResponse cart = fetchCheckoutCart(userId);
        Order order = createOrder(userId);
        List<OrderItem> orderItemList = createOrderItems(order, cart);
        order.setOrderItems(new HashSet<>(orderItemList));
        order.setTotalAmount(calculateTotalAmount(orderItemList));
        Order savedOrder = orderRepository.save(order);
        clearRemoteCart(cart.getCartId());
        kafkaTemplate.send("order-placed", savedOrder.getOrderId().toString(),
                new OrderPlacedEvent(savedOrder.getOrderId(), userId, savedOrder.getTotalAmount()));
        return savedOrder;
    }

    private CartCheckoutResponse fetchCheckoutCart(Long userId) {
        String url = ECOMM_BASE_URL + "/carts/user/" + userId + "/checkout-cart";
        ResponseEntity<ApiResponseWrapper<CartCheckoutResponse>> response = restTemplate.exchange(
                url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        CartCheckoutResponse body = response.getBody() == null ? null : response.getBody().getData();
        if (body == null) {
            throw new ResourceNotFoundException("Cart not found for user " + userId);
        }
        return body;
    }

    private void clearRemoteCart(Long cartId) {
        restTemplate.delete(ECOMM_BASE_URL + "/carts/" + cartId + "/clear");
    }

    private void decreaseRemoteInventory(Long productId, int quantity) {
        String url = ECOMM_BASE_URL + "/products/product/" + productId + "/decrease-inventory?quantity=" + quantity;
        restTemplate.postForObject(url, null, ApiResponseWrapper.class);
    }

    private Order createOrder(Long userId) {
        Order order = new Order();
        order.setUserId(userId);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setOrderDate(LocalDate.now());
        return order;
    }

    private List<OrderItem> createOrderItems(Order order, CartCheckoutResponse cart) {
        List<CompletableFuture<OrderItem>> futures = cart.getItems().stream().map(
                item -> CompletableFuture.supplyAsync(() -> {
                    decreaseRemoteInventory(item.getProductId(), item.getQuantity());
                    return new OrderItem(order, item.getProductId(), item.getProductName(), item.getQuantity(), item.getUnitPrice());
                }, inventoryUpdateExecutor)
        ).toList();
        return futures.stream().map(CompletableFuture::join).toList();
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
