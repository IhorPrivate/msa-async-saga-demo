package ua.nure.msa.common.contracts;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderCreated(UUID orderId, String sku, int quantity, BigDecimal amount) {
}
