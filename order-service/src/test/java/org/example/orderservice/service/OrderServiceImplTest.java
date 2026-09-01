package org.example.orderservice.service;

import org.example.orderservice.Enums.OrderStatus;
import org.example.orderservice.client.CartCheckoutItemResponse;
import org.example.orderservice.client.CartCheckoutResponse;
import org.example.orderservice.client.EcommClient;
import org.example.orderservice.dto.OrderPlacedEvent;
import org.example.orderservice.exception.OrderPlacementFailedException;
import org.example.orderservice.pojo.Order;
import org.example.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ModelMapper modelMapper;
    @Mock
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    @Mock
    private EcommClient ecommClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private static final Long USER_ID = 42L;
    private static final String AUTH_HEADER = "Bearer token";

    private CartCheckoutItemResponse item(Long productId, int quantity) {
        CartCheckoutItemResponse item = new CartCheckoutItemResponse();
        item.setProductId(productId);
        item.setProductName("product-" + productId);
        item.setQuantity(quantity);
        item.setUnitPrice(BigDecimal.TEN);
        return item;
    }

    private CartCheckoutResponse cart(CartCheckoutItemResponse... items) {
        CartCheckoutResponse cart = new CartCheckoutResponse();
        cart.setCartId(7L);
        cart.setItems(List.of(items));
        return cart;
    }

    @Test
    void placeOrder_allStepsSucceed_confirmsOrderAndPublishesEvent() {
        CartCheckoutItemResponse item1 = item(1L, 2);
        CartCheckoutItemResponse item2 = item(2L, 3);
        when(ecommClient.fetchCheckoutCart(USER_ID, AUTH_HEADER)).thenReturn(cart(item1, item2));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setOrderId(100L);
            return order;
        });

        Order result = orderService.placeOrder(USER_ID, AUTH_HEADER);

        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(ecommClient, never()).restoreInventory(anyLong(), anyInt());
        verify(kafkaTemplate, times(1)).send(eq("order-placed"), eq("100"), any(OrderPlacedEvent.class));
    }

    @Test
    void placeOrder_secondItemInventoryFails_compensatesFirstItemOnly() {
        CartCheckoutItemResponse item1 = item(1L, 2);
        CartCheckoutItemResponse item2 = item(2L, 3);
        when(ecommClient.fetchCheckoutCart(USER_ID, AUTH_HEADER)).thenReturn(cart(item1, item2));
        doNothing().when(ecommClient).decreaseInventory(1L, 2);
        doThrow(new IllegalStateException("ecomm down")).when(ecommClient).decreaseInventory(2L, 3);

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, AUTH_HEADER))
                .isInstanceOf(OrderPlacementFailedException.class);

        verify(ecommClient, times(1)).restoreInventory(1L, 2);
        verify(ecommClient, never()).restoreInventory(eq(2L), anyInt());
        verify(orderRepository, never()).save(any(Order.class));
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void placeOrder_firstItemInventoryFails_noCompensationCalled() {
        CartCheckoutItemResponse item1 = item(1L, 2);
        when(ecommClient.fetchCheckoutCart(USER_ID, AUTH_HEADER)).thenReturn(cart(item1));
        doThrow(new IllegalStateException("ecomm down")).when(ecommClient).decreaseInventory(1L, 2);

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, AUTH_HEADER))
                .isInstanceOf(OrderPlacementFailedException.class);

        verify(ecommClient, never()).restoreInventory(anyLong(), anyInt());
    }

    @Test
    void placeOrder_orderSaveFails_compensatesAllReservedItems() {
        CartCheckoutItemResponse item1 = item(1L, 2);
        CartCheckoutItemResponse item2 = item(2L, 3);
        when(ecommClient.fetchCheckoutCart(USER_ID, AUTH_HEADER)).thenReturn(cart(item1, item2));
        when(orderRepository.save(any(Order.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, AUTH_HEADER))
                .isInstanceOf(OrderPlacementFailedException.class);

        verify(ecommClient, times(1)).restoreInventory(1L, 2);
        verify(ecommClient, times(1)).restoreInventory(2L, 3);
    }

    @Test
    void placeOrder_compensationCallItselfThrows_doesNotAbortRemainingCompensations() {
        CartCheckoutItemResponse item1 = item(1L, 1);
        CartCheckoutItemResponse item2 = item(2L, 2);
        CartCheckoutItemResponse item3 = item(3L, 3);
        when(ecommClient.fetchCheckoutCart(USER_ID, AUTH_HEADER)).thenReturn(cart(item1, item2, item3));
        when(orderRepository.save(any(Order.class))).thenThrow(new RuntimeException("db down"));
        doNothing().when(ecommClient).restoreInventory(1L, 1);
        doThrow(new RuntimeException("restore failed")).when(ecommClient).restoreInventory(2L, 2);
        doNothing().when(ecommClient).restoreInventory(3L, 3);

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, AUTH_HEADER))
                .isInstanceOf(OrderPlacementFailedException.class);

        verify(ecommClient, times(1)).restoreInventory(1L, 1);
        verify(ecommClient, times(1)).restoreInventory(2L, 2);
        verify(ecommClient, times(1)).restoreInventory(3L, 3);
    }

    @Test
    void placeOrder_cartClearFails_orderStillConfirmedNoCompensation() {
        CartCheckoutItemResponse item1 = item(1L, 2);
        when(ecommClient.fetchCheckoutCart(USER_ID, AUTH_HEADER)).thenReturn(cart(item1));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setOrderId(200L);
            return order;
        });
        doThrow(new IllegalStateException("ecomm down")).when(ecommClient).clearCart(7L, AUTH_HEADER);

        Order result = orderService.placeOrder(USER_ID, AUTH_HEADER);

        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(ecommClient, never()).restoreInventory(anyLong(), anyInt());
        verify(kafkaTemplate, times(1)).send(eq("order-placed"), eq("200"), any(OrderPlacedEvent.class));
    }
}
