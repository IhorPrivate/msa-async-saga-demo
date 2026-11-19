package ua.nure.msa.common.contracts;

import java.util.UUID;

public record PaymentCompleted(UUID orderId, UUID paymentId) {
}
