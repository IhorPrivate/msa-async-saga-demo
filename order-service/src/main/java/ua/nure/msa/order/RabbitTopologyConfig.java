package ua.nure.msa.order;

import java.util.List;

import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ua.nure.msa.common.messaging.OrchestrationMode;
import ua.nure.msa.common.messaging.Topology;

/** Топологія order-service: п'ять черг, з яких він слухає події саги (в обох режимах). */
@Configuration
public class RabbitTopologyConfig {

    @Bean
    public Declarables orderQueues() { // [S2]
        // Кожна подія має власну чергу, щоб повільний обробник не блокував решту
        List<Declarable> all = List.of(
                Topology.eventQueue("order.on-stock-reserved", Topology.RK_STOCK_RESERVED),
                Topology.eventQueue("order.on-stock-rejected", Topology.RK_STOCK_REJECTED))
                .stream().flatMap(d -> d.getDeclarables().stream()).toList();
        return new Declarables(all);
    }

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
