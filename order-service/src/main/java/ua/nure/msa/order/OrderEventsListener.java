package ua.nure.msa.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.nure.msa.common.contracts.OrderCancelled;
import ua.nure.msa.common.contracts.OrderCompleted;
import ua.nure.msa.common.contracts.PaymentCompleted;
import ua.nure.msa.common.contracts.PaymentFailed;
import ua.nure.msa.common.contracts.StockRejected;
import ua.nure.msa.common.contracts.StockReleased;
import ua.nure.msa.common.contracts.StockReserved;
import ua.nure.msa.common.messaging.ChoreographyMode;
import ua.nure.msa.common.messaging.Inbox;
import ua.nure.msa.common.messaging.Outbox;
import ua.nure.msa.common.messaging.Topology;

import static ua.nure.msa.order.OrderStatus.CANCELLED;
import static ua.nure.msa.order.OrderStatus.CANCELLING;
import static ua.nure.msa.order.OrderStatus.COMPLETED;
import static ua.nure.msa.order.OrderStatus.PENDING;
import static ua.nure.msa.order.OrderStatus.STOCK_RESERVED;

/** Хореографія: order-service реагує на події інших сервісів і сам рухає статус замовлення. */
@Component
@ChoreographyMode // [S6]
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final OrderRepository orders;
    private final Outbox outbox;
    private final Inbox inbox;

    public OrderEventsListener(OrderRepository orders, Outbox outbox, Inbox inbox) {
        this.orders = orders;
        this.outbox = outbox;
        this.inbox = inbox;
    }

    @RabbitListener(queues = "order.on-stock-reserved") // [S2]
    @Transactional
    public void onStockReserved(StockReserved e, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
        if (!inbox.firstTime(messageId, "order.on-stock-reserved")) return;
        boolean moved = orders.transition(e.orderId(), STOCK_RESERVED, PENDING);
        log.info("stock.reserved order={} moved={}", e.orderId(), moved);
    }

    @RabbitListener(queues = "order.on-stock-rejected") // [S2]
    @Transactional
    public void onStockRejected(StockRejected e, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
        if (!inbox.firstTime(messageId, "order.on-stock-rejected")) return;
        boolean moved = orders.transition(e.orderId(), CANCELLED, PENDING);
        // Подію про скасування шлемо лише якщо стан справді змінився, інакше буде дубль
        if (moved) outbox.event(e.orderId(), Topology.RK_ORDER_CANCELLED, new OrderCancelled(e.orderId(), e.reason())); // [S4]
        log.info("stock.rejected order={} moved={}", e.orderId(), moved);
    }

    @RabbitListener(queues = "order.on-payment-completed") // [S6]
    @Transactional
    public void onPaymentCompleted(PaymentCompleted e, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
        if (!inbox.firstTime(messageId, "order.on-payment-completed")) return;
        // PENDING теж дозволено: stock.reserved може прийти пізніше за payment.completed
        boolean moved = orders.transition(e.orderId(), COMPLETED, PENDING, STOCK_RESERVED);
        if (moved) outbox.event(e.orderId(), Topology.RK_ORDER_COMPLETED, new OrderCompleted(e.orderId()));
        log.info("payment.completed order={} moved={}", e.orderId(), moved);
    }

    @RabbitListener(queues = "order.on-payment-failed") // [S6]
    @Transactional
    public void onPaymentFailed(PaymentFailed e, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
        if (!inbox.firstTime(messageId, "order.on-payment-failed")) return;
        // Ще не скасовано: чекаємо stock.released від inventory (компенсація)
        boolean moved = orders.transition(e.orderId(), CANCELLING, PENDING, STOCK_RESERVED);
        log.info("payment.failed order={} moved={}", e.orderId(), moved);
    }

    @RabbitListener(queues = "order.on-stock-released") // [S6]
    @Transactional
    public void onStockReleased(StockReleased e, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
        if (!inbox.firstTime(messageId, "order.on-stock-released")) return;
        boolean moved = orders.transition(e.orderId(), CANCELLED, PENDING, STOCK_RESERVED, CANCELLING);
        if (moved) outbox.event(e.orderId(), Topology.RK_ORDER_CANCELLED, new OrderCancelled(e.orderId(), "payment failed"));
        log.info("stock.released order={} moved={}", e.orderId(), moved);
    }
}
