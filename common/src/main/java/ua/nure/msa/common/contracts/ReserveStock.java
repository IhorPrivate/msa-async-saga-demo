package ua.nure.msa.common.contracts;

import java.util.UUID;

public record ReserveStock(UUID orderId, String sku, int quantity) {
}
