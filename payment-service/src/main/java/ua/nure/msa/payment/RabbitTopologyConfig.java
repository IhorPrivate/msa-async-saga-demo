package ua.nure.msa.payment;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
}
