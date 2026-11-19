package ua.nure.msa.inventory;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.nure.msa.common.messaging.ChoreographyMode;
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

    /** У хореографії сервіс сам підписується на події інших. */
    @ChoreographyMode
    @Configuration
    static class Choreography {
        @Bean // [S6]
        Declarables inventoryOnOrderCreated() {
            return Topology.eventQueue(InventoryChoreographyListener.Q_ON_ORDER_CREATED, Topology.RK_ORDER_CREATED);
        }

        @Bean // [S6]
        Declarables inventoryOnPaymentFailed() {
            return Topology.eventQueue(InventoryChoreographyListener.Q_ON_PAYMENT_FAILED, Topology.RK_PAYMENT_FAILED);
        }
    }
}
