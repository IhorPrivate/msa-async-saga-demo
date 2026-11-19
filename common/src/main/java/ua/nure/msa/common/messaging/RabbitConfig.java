package ua.nure.msa.common.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableScheduling // потрібно для @Scheduled у OutboxRelay
class RabbitConfig {

    // Єдиний бін MessageConverter: Boot сам підключає його до RabbitTemplate і до listener-контейнерів.
    // Беремо JsonMapper від Boot, щоб JSON у черзі і в REST мав однакові налаштування.
    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(Topology.EVENTS_EXCHANGE);
    }

    // Fanout: у DLQ має потрапити все, що відхилено, незалежно від routing key
    @Bean
    FanoutExchange deadLetterExchange() {
        return new FanoutExchange(Topology.DLX);
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(Topology.DLQ).quorum().build();
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, FanoutExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange);
    }
}
