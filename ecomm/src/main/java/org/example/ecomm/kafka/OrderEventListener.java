package org.example.ecomm.kafka;

import lombok.extern.slf4j.Slf4j;
import org.example.ecomm.dto.OrderPlacedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderEventListener {

    @KafkaListener(topics = "order-placed", groupId = "ecomm-notification")
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("Order placed event received: orderId={}, userId={}, totalAmount={}",
                event.orderId(), event.userId(), event.totalAmount());
    }
}
