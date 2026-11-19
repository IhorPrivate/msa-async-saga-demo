package ua.nure.msa.order;

/** Стани замовлення в сазі; CANCELLING — проміжний стан під час компенсації. */
public enum OrderStatus {
    PENDING, STOCK_RESERVED, COMPLETED, CANCELLING, CANCELLED
}
