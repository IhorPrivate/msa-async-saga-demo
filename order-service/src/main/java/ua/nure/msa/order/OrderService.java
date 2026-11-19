package ua.nure.msa.order;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Старт саги: зберігає замовлення. */
@Service
public class OrderService {

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional
    public Order create(String sku, int quantity, BigDecimal amount) {
        Order order = new Order(UUID.randomUUID(), sku, quantity, amount, OrderStatus.PENDING);
        orders.insert(order);
        // TODO [S1]: надіслати команду ReserveStock
        return order;
    }
}
