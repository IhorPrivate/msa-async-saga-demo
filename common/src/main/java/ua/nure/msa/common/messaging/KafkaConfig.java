package ua.nure.msa.common.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
class KafkaConfig {

    // 3 партиції, щоб на лекції було видно розподіл за ключем orderId; RF 1, бо брокер один
    @Bean
    NewTopic eventsTopic() {
        return TopicBuilder.name(Topology.KAFKA_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
