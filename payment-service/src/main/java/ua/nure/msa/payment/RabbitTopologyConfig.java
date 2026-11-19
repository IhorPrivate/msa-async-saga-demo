package ua.nure.msa.payment;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.nure.msa.common.messaging.ChoreographyMode;
import ua.nure.msa.common.messaging.OrchestrationMode;
import ua.nure.msa.common.messaging.Topology;

/** Черги payment-service; черги неактивного режиму саги не оголошуються. */
@Configuration
public class RabbitTopologyConfig {

    /** Команда оплати існує лише в оркестрації. */
    @OrchestrationMode
    @Configuration
    static class Orchestration {
        @Bean
        Queue paymentChargeQueue() {
            return Topology.commandQueue(Topology.Q_PAYMENT_CHARGE);
        }
    }

    /** У хореографії сервіс підписується на події order.created та stock.reserved. */
    @ChoreographyMode
    @Configuration
    static class Choreography {
        @Bean // [S6]
        Declarables paymentOnOrderCreated() {
            return Topology.eventQueue(PaymentChoreographyListener.Q_ON_ORDER_CREATED, Topology.RK_ORDER_CREATED);
        }

        @Bean // [S6]
        Declarables paymentOnStockReserved() {
            return Topology.eventQueue(PaymentChoreographyListener.Q_ON_STOCK_RESERVED, Topology.RK_STOCK_RESERVED);
        }
    }
}
