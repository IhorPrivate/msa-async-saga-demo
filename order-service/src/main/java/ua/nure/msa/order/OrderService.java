package ua.nure.msa.order;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.nure.msa.common.contracts.OrderCreated;
import ua.nure.msa.common.messaging.Outbox;
import ua.nure.msa.common.messaging.Topology;

/** Старт саги: зберігає замовлення й у тій самій транзакції кладе перше повідомлення в outbox. */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final Outbox outbox;

    public OrderService(OrderRepository orders, Outbox outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    @Transactional
    public Order create(String sku, int quantity, BigDecimal amount) {
        Order order = new Order(UUID.randomUUID(), sku, quantity, amount, OrderStatus.PENDING);
        orders.insert(order);
        // Запис у БД і повідомлення — одна транзакція, тому dual write неможливий
        outbox.event(order.id(), Topology.RK_ORDER_CREATED, new OrderCreated(order.id(), sku, quantity, amount)); // [S6]
        return order;
    }
}
