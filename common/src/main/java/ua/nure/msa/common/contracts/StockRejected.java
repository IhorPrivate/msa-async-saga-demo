package ua.nure.msa.common.contracts;

import java.util.UUID;

public record StockRejected(UUID orderId, String reason) {
}
