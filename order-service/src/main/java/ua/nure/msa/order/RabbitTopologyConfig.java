package ua.nure.msa.order;

import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ua.nure.msa.common.messaging.OrchestrationMode;
import ua.nure.msa.common.messaging.Topology;

/** Топологія order-service: п'ять черг, з яких він слухає події саги (в обох режимах). */
@Configuration
public class RabbitTopologyConfig {

    // TODO [S2]: черги подій order-service

    /**
     * Оркестратор шле команди в черги учасників. Publisher confirm не рятує від неоголошеної черги:
     * брокер підтверджує й мовчки відкидає немаршрутизоване повідомлення. Тому оголошуємо черги й тут —
     * з тими самими аргументами, що й учасники (повторне однакове оголошення безпечне).
     */
    @OrchestrationMode
    @Configuration
    static class Orchestration {
        @Bean
        Declarables orchestratorCommandQueues() {
            return new Declarables(
                    Topology.commandQueue(Topology.Q_INVENTORY_RESERVE),
                    Topology.commandQueue(Topology.Q_PAYMENT_CHARGE),
                    Topology.commandQueue(Topology.Q_INVENTORY_RELEASE));
        }
    }
}
