package ua.nure.msa.common.messaging;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Патерн Polling Publisher: періодично читає outbox і доставляє повідомлення в брокери (at-least-once). */
@Component
public class OutboxRelay {

    private static final long CONFIRM_TIMEOUT_MS = 5_000;
    private static final long KAFKA_TIMEOUT_SECONDS = 5;

    private record Pending(UUID id, UUID aggregateId, String exchange, String routingKey, String payload) {
    }

    private final JdbcClient jdbc;
    private final RabbitTemplate rabbit;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(JdbcClient jdbc, RabbitTemplate rabbit, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-ms:500}")
    @Transactional
    public void publishPending() { // [S4]
        // TODO [S4]: SELECT … FOR UPDATE SKIP LOCKED → publishToRabbit → publishToKafka (лише події) → UPDATE published_at
    }

    private void publishToRabbit(Pending row) {
        // Без заголовка __TypeId__: тип визначає параметр методу слухача, а не відправник
        Message message = MessageBuilder.withBody(row.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(row.id().toString())
                .setCorrelationId(row.aggregateId().toString())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        // invoke тримає один канал, щоб дочекатися підтвердження брокера (publisher confirm)
        rabbit.invoke(operations -> {
            operations.send(row.exchange(), row.routingKey(), message);
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return Boolean.TRUE;
        });
    }

    private void publishToKafka(Pending row) {
        // Ключ = orderId: усі події одного замовлення потрапляють в одну партицію й зберігають порядок
        ProducerRecord<String, String> record = new ProducerRecord<>(
                Topology.KAFKA_EVENTS_TOPIC, null, row.aggregateId().toString(), row.payload());
        record.headers().add("type", row.routingKey().getBytes(StandardCharsets.UTF_8));
        record.headers().add("messageId", row.id().toString().getBytes(StandardCharsets.UTF_8));
        try {
            kafka.send(record).get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka send interrupted for outbox row " + row.id(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka send failed for outbox row " + row.id(), e);
        }
    }
}
