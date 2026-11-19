package ua.nure.msa.order;

import java.math.BigDecimal;
import java.util.UUID;

/** Замовлення — агрегат, який зберігає order-service і яким керує сага. */
public record Order(UUID id, String sku, int quantity, BigDecimal amount, OrderStatus status) {
}
