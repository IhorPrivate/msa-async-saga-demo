package ua.nure.msa.order;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.nure.msa.common.contracts.ReserveStock;
import ua.nure.msa.common.messaging.Topology;

/** Старт саги: зберігає замовлення й шле команду складу. */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final RabbitTemplate rabbit;

    public OrderService(OrderRepository orders, RabbitTemplate rabbit) {
        this.orders = orders;
        this.rabbit = rabbit;
    }

    @Transactional
    public Order create(String sku, int quantity, BigDecimal amount) {
        Order order = new Order(UUID.randomUUID(), sku, quantity, amount, OrderStatus.PENDING);
        orders.insert(order);
        rabbit.convertAndSend(Topology.Q_INVENTORY_RESERVE, new ReserveStock(order.id(), sku, quantity)); // [S1] default exchange: routing key = ім'я черги
        return order;
    }
}
