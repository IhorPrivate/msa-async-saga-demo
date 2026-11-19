package ua.nure.msa.common.contracts;

import java.math.BigDecimal;
import java.util.UUID;

public record ChargePayment(UUID orderId, BigDecimal amount) {
}
