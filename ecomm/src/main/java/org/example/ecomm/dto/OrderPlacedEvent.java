package org.example.ecomm.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public record OrderPlacedEvent(Long orderId, Long userId, BigDecimal totalAmount) implements Serializable {
}
