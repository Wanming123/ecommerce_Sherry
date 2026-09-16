package org.example.orderservice.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    // 6 partitions: keyed by orderId, so ordering per order is unaffected by partition count.
    // Sized for headroom so notification-service and warehouse-service consumer groups can
    // each scale to several instances later without a disruptive repartition.
    // replicas=1 matches the single local broker this runs against; production would use 3.
    @Bean
    public NewTopic orderPlacedTopic() {
        return TopicBuilder.name("order-placed")
                .partitions(6)
                .replicas(1)
                .build();
    }
}
