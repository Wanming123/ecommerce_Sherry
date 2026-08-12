package org.example.orderservice.kafka;

import lombok.extern.slf4j.Slf4j;
import org.example.orderservice.dto.OrderPlacedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderEventListener {

    @KafkaListener(topics = "order-placed", groupId = "order-service-notification")
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("Order placed event received: orderId={}, userId={}, totalAmount={}",
                event.orderId(), event.userId(), event.totalAmount());
    }
}
