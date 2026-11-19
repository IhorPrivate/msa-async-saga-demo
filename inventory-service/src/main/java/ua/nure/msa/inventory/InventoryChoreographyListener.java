package ua.nure.msa.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.msa.common.contracts.OrderCreated;
import ua.nure.msa.common.contracts.PaymentFailed;
import ua.nure.msa.common.contracts.StockRejected;
import ua.nure.msa.common.contracts.StockReleased;
import ua.nure.msa.common.contracts.StockReserved;
import ua.nure.msa.common.messaging.ChoreographyMode;
import ua.nure.msa.common.messaging.Inbox;
import ua.nure.msa.common.messaging.Outbox;
import ua.nure.msa.common.messaging.Topology;

/** Хореографія: склад сам реагує на order.created та payment.failed. */
@Component // [S6]
@ChoreographyMode // [S6]
public class InventoryChoreographyListener {

    static final String Q_ON_ORDER_CREATED = "inventory.on-order-created";
    static final String Q_ON_PAYMENT_FAILED = "inventory.on-payment-failed";

    private static final Logger log = LoggerFactory.getLogger(InventoryChoreographyListener.class);

    private final InventoryService inventory;
    private final Outbox outbox;
    private final Inbox inbox;

    public InventoryChoreographyListener(InventoryService inventory, Outbox outbox, Inbox inbox) {
        this.inventory = inventory;
        this.outbox = outbox;
        this.inbox = inbox;
    }

    @RabbitListener(queues = Q_ON_ORDER_CREATED) // [S6]
    @Transactional
    public void onOrderCreated(OrderCreated e, @Header(AmqpHeaders.MESSAGE_ID) String messageId) { // [S6]
        if (!inbox.firstTime(messageId, Q_ON_ORDER_CREATED)) return;
        boolean ok = inventory.reserve(e.orderId(), e.sku(), e.quantity());
        log.info("[inventory] order.created order={} ok={}", e.orderId(), ok);
        if (ok) outbox.event(e.orderId(), Topology.RK_STOCK_RESERVED, new StockReserved(e.orderId()));
        else outbox.event(e.orderId(), Topology.RK_STOCK_REJECTED, new StockRejected(e.orderId(), "insufficient stock"));
    }

    @RabbitListener(queues = Q_ON_PAYMENT_FAILED) // [S6]
    @Transactional
    public void onPaymentFailed(PaymentFailed e, @Header(AmqpHeaders.MESSAGE_ID) String messageId) { // [S6]
        if (!inbox.firstTime(messageId, Q_ON_PAYMENT_FAILED)) return;
        inventory.release(e.orderId()); // компенсація
        log.info("[inventory] payment.failed order={} -> released", e.orderId());
        outbox.event(e.orderId(), Topology.RK_STOCK_RELEASED, new StockReleased(e.orderId()));
    }
}
