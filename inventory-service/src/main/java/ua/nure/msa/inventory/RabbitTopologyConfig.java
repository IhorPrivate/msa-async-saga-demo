package ua.nure.msa.inventory;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.nure.msa.common.messaging.OrchestrationMode;
import ua.nure.msa.common.messaging.Topology;

/** Черги inventory-service; черги неактивного режиму саги не оголошуються. */
@Configuration
public class RabbitTopologyConfig {

    @Bean // [S1]
    Queue inventoryReserveQueue() {
        return Topology.commandQueue(Topology.Q_INVENTORY_RESERVE);
    }

    /** Команда компенсації існує лише в оркестрації. */
    @OrchestrationMode
    @Configuration
    static class Orchestration {
        @Bean
        Queue inventoryReleaseQueue() {
            return Topology.commandQueue(Topology.Q_INVENTORY_RELEASE);
        }
    }
}
