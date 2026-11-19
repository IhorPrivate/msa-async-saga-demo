package ua.nure.msa.payment;

import java.util.UUID;

/** Результат списання: успіх, id платежу та причина відмови. */
public record PaymentResult(boolean ok, UUID paymentId, String reason) {}
