package ua.nure.msa.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.nure.msa.common.contracts.ChargePayment;
import ua.nure.msa.common.contracts.OrderCancelled;
import ua.nure.msa.common.contracts.OrderCompleted;
import ua.nure.msa.common.contracts.PaymentCompleted;
import ua.nure.msa.common.contracts.PaymentFailed;
import ua.nure.msa.common.contracts.ReleaseStock;
import ua.nure.msa.common.contracts.ReserveStock;
import ua.nure.msa.common.contracts.StockRejected;
import ua.nure.msa.common.contracts.StockReleased;
import ua.nure.msa.common.contracts.StockReserved;
import ua.nure.msa.common.messaging.Inbox;
import ua.nure.msa.common.messaging.OrchestrationMode;
import ua.nure.msa.common.messaging.Outbox;
import ua.nure.msa.common.messaging.Topology;

import static ua.nure.msa.order.OrderStatus.CANCELLED;
import static ua.nure.msa.order.OrderStatus.CANCELLING;
import static ua.nure.msa.order.OrderStatus.COMPLETED;
import static ua.nure.msa.order.OrderStatus.PENDING;
import static ua.nure.msa.order.OrderStatus.STOCK_RESERVED;

/** Оркестрація: одна state machine в order-service керує сагою командами, учасники лише виконують. */
@Component
@OrchestrationMode
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final OrderRepository orders;
    private final Outbox outbox;
    private final Inbox inbox;

    public OrderSagaOrchestrator(OrderRepository orders, Outbox outbox, Inbox inbox) {
        this.orders = orders;
        this.outbox = outbox;
        this.inbox = inbox;
    }

    /** Викликається з OrderService.create в тій самій транзакції, що й insert замовлення. */
    public void start(Order order) {
        outbox.command(order.id(), Topology.Q_INVENTORY_RESERVE,
                new ReserveStock(order.id(), order.sku(), order.quantity()));
    }

    @RabbitListener(queues = "order.on-stock-reserved")
    @Transactional
    public void onStockReserved(StockReserved e, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
        if (!inbox.firstTime(messageId, "order.on-stock-reserved")) return;
        boolean moved = orders.transition(e.orderId(), STOCK_RESERVED, PENDING);
        // Суму беремо з БД: подія StockReserved несе лише orderId
        if (moved) orders.find(e.orderId()).ifPresent(o ->
                outbox.command(o.id(), Topology.Q_PAYMENT_CHARGE, new ChargePayment(o.id(), o.amount())));
        log.info("[orchestrator] stock.reserved order={} moved={}", e.orderId(), moved);
    }

    @RabbitListener(queues = "order.on-stock-rejected")
    @Transactional
    public void onStockRejected(StockRejected e, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
        if (!inbox.firstTime(messageId, "order.on-stock-rejected")) return;
        boolean moved = orders.transition(e.orderId(), CANCELLED, PENDING);
        if (moved) outbox.event(e.orderId(), Topology.RK_ORDER_CANCELLED, new OrderCancelled(e.orderId(), e.reason()));
        log.info("[orchestrator] stock.rejected order={} moved={}", e.orderId(), moved);
    }

    @RabbitListener(queues = "order.on-payment-completed")
    @Transactional
    public void onPaymentCompleted(PaymentCompleted e, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
        if (!inbox.firstTime(messageId, "order.on-payment-completed")) return;
        boolean moved = orders.transition(e.orderId(), COMPLETED, STOCK_RESERVED);
        if (moved) outbox.event(e.orderId(), Topology.RK_ORDER_COMPLETED, new OrderCompleted(e.orderId()));
        log.info("[orchestrator] payment.completed order={} moved={}", e.orderId(), moved);
    }

    @RabbitListener(queues = "order.on-payment-failed")
    @Transactional
    public void onPaymentFailed(PaymentFailed e, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
        if (!inbox.firstTime(messageId, "order.on-payment-failed")) return;
        boolean moved = orders.transition(e.orderId(), CANCELLING, STOCK_RESERVED);
        // Компенсація: оркестратор сам наказує зняти резерв
        if (moved) outbox.command(e.orderId(), Topology.Q_INVENTORY_RELEASE, new ReleaseStock(e.orderId()));
        log.info("[orchestrator] payment.failed order={} moved={}", e.orderId(), moved);
    }

    @RabbitListener(queues = "order.on-stock-released")
    @Transactional
    public void onStockReleased(StockReleased e, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
        if (!inbox.firstTime(messageId, "order.on-stock-released")) return;
        boolean moved = orders.transition(e.orderId(), CANCELLED, CANCELLING);
        if (moved) outbox.event(e.orderId(), Topology.RK_ORDER_CANCELLED, new OrderCancelled(e.orderId(), "payment failed"));
        log.info("[orchestrator] stock.released order={} moved={}", e.orderId(), moved);
    }
}
