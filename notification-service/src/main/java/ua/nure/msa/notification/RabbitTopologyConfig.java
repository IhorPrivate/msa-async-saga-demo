package ua.nure.msa.notification;

import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.nure.msa.common.messaging.Topology;

/** Черга аудиту, прив'язана до shop.events із binding "#". */
@Configuration
public class RabbitTopologyConfig {

    @Bean // [S2]
    Declarables auditQueue() {
        return Topology.eventQueue(AuditListener.QUEUE, "#");
    }
}
