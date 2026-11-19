package ua.nure.msa.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.msa.common.contracts.ChargePayment;
import ua.nure.msa.common.contracts.PaymentCompleted;
import ua.nure.msa.common.contracts.PaymentFailed;
import ua.nure.msa.common.messaging.Outbox;
import ua.nure.msa.common.messaging.OrchestrationMode;
import ua.nure.msa.common.messaging.Topology;

/** Оркестрація: виконує команду ChargePayment (open + charge). */
@Component
@OrchestrationMode
public class ChargePaymentListener {

    private static final Logger log = LoggerFactory.getLogger(ChargePaymentListener.class);

    private final PaymentService payments;
    private final Outbox outbox;

    public ChargePaymentListener(PaymentService payments, Outbox outbox) {
        this.payments = payments;
        this.outbox = outbox;
    }

    @RabbitListener(queues = Topology.Q_PAYMENT_CHARGE)
    @Transactional
    public void on(ChargePayment cmd) {
        payments.open(cmd.orderId(), cmd.amount()); // у цьому режимі немає order.created, тож відкриваємо тут
        var r = payments.charge(cmd.orderId());
        log.info("[payment] charge order={} ok={}", cmd.orderId(), r.ok());
        if (r.ok()) outbox.event(cmd.orderId(), Topology.RK_PAYMENT_COMPLETED, new PaymentCompleted(cmd.orderId(), r.paymentId()));
        else outbox.event(cmd.orderId(), Topology.RK_PAYMENT_FAILED, new PaymentFailed(cmd.orderId(), r.reason()));
    }
}
