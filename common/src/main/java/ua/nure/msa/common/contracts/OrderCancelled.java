package ua.nure.msa.common.contracts;

import java.util.UUID;

public record OrderCancelled(UUID orderId, String reason) {
}
