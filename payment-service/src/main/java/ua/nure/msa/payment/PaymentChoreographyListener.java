package ua.nure.msa.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.msa.common.contracts.OrderCreated;
import ua.nure.msa.common.contracts.PaymentCompleted;
import ua.nure.msa.common.contracts.PaymentFailed;
import ua.nure.msa.common.contracts.StockReserved;
import ua.nure.msa.common.messaging.ChoreographyMode;
import ua.nure.msa.common.messaging.Outbox;
import ua.nure.msa.common.messaging.Topology;

/** Хореографія: оплата сама реагує на order.created та stock.reserved. */
@Component // [S6]
@ChoreographyMode // [S6]
public class PaymentChoreographyListener {

    static final String Q_ON_ORDER_CREATED = "payment.on-order-created";
    static final String Q_ON_STOCK_RESERVED = "payment.on-stock-reserved";

    private static final Logger log = LoggerFactory.getLogger(PaymentChoreographyListener.class);

    private final PaymentService payments;
    private final Outbox outbox;

    public PaymentChoreographyListener(PaymentService payments, Outbox outbox) {
        this.payments = payments;
        this.outbox = outbox;
    }

    @RabbitListener(queues = Q_ON_ORDER_CREATED) // [S6]
    @Transactional
    public void onOrderCreated(OrderCreated e) { // [S6]
        payments.open(e.orderId(), e.amount()); // open ідемпотентний (UNIQUE), inbox не потрібен
        log.info("[payment] opened order={} amount={}", e.orderId(), e.amount());
    }

    @RabbitListener(queues = Q_ON_STOCK_RESERVED) // [S6]
    @Transactional
    public void onStockReserved(StockReserved e) { // [S6]
        var r = payments.charge(e.orderId());
        log.info("[payment] charge order={} ok={}", e.orderId(), r.ok());
        if (r.ok()) outbox.event(e.orderId(), Topology.RK_PAYMENT_COMPLETED, new PaymentCompleted(e.orderId(), r.paymentId()));
        else outbox.event(e.orderId(), Topology.RK_PAYMENT_FAILED, new PaymentFailed(e.orderId(), r.reason()));
    }
}
