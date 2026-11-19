package ua.nure.msa.common.messaging;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/** Імена exchange, черг і routing key усієї системи + фабрики черг (єдине місце правди для топології). */
public final class Topology {

    public static final String EVENTS_EXCHANGE = "shop.events";
    public static final String DLX = "shop.dlx";
    public static final String DLQ = "shop.dlq";
    public static final String KAFKA_EVENTS_TOPIC = "shop.events";

    // Черги команд: default exchange, routing key = ім'я черги
    public static final String Q_INVENTORY_RESERVE = "inventory.reserve";
    public static final String Q_INVENTORY_RELEASE = "inventory.release";
    public static final String Q_PAYMENT_CHARGE = "payment.charge";

    // Routing keys подій
    public static final String RK_ORDER_CREATED = "order.created";
    public static final String RK_ORDER_COMPLETED = "order.completed";
    public static final String RK_ORDER_CANCELLED = "order.cancelled";
    public static final String RK_STOCK_RESERVED = "stock.reserved";
    public static final String RK_STOCK_REJECTED = "stock.rejected";
    public static final String RK_STOCK_RELEASED = "stock.released";
    public static final String RK_PAYMENT_COMPLETED = "payment.completed";
    public static final String RK_PAYMENT_FAILED = "payment.failed";

    private Topology() {
    }

    /** Черга події + exchange + binding. Exchange оголошується повторно: однакове оголошення безпечне. */
    public static Declarables eventQueue(String queue, String routingKey) {
        Queue q = commandQueue(queue);
        TopicExchange events = new TopicExchange(EVENTS_EXCHANGE);
        return new Declarables(q, events, BindingBuilder.bind(q).to(events).with(routingKey));
    }

    /** Quorum-черга (переживає втрату вузла) з DLX: відхилені повідомлення потрапляють у shop.dlq. */
    public static Queue commandQueue(String queue) {
        return QueueBuilder.durable(queue)
                .quorum()
                .deadLetterExchange(DLX)
                .build();
    }
}
