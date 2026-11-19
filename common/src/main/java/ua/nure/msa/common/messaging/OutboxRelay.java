package ua.nure.msa.common.messaging;

import java.nio.charset.StandardCharsets;
import java.util.List;
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
        // SKIP LOCKED: кілька екземплярів сервісу не чіпають рядки один одного
        List<Pending> batch = jdbc.sql("""
                        SELECT id, aggregate_id, exchange, routing_key, payload
                        FROM outbox
                        WHERE published_at IS NULL
                        ORDER BY created_at
                        LIMIT 50
                        FOR UPDATE SKIP LOCKED
                        """)
                .query((rs, rowNum) -> new Pending(
                        rs.getObject("id", UUID.class),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("exchange"),
                        rs.getString("routing_key"),
                        rs.getString("payload")))
                .list();

        for (Pending row : batch) {
            publishToRabbit(row);
            if (Topology.EVENTS_EXCHANGE.equals(row.exchange())) {
                publishToKafka(row);
            }
            // Будь-який збій вище кидає виняток: транзакція відкочується, рядок буде повторено.
            // Тому доставка at-least-once, а споживачі мають бути ідемпотентними (Inbox).
            jdbc.sql("UPDATE outbox SET published_at = now() WHERE id = ?")
                    .param(row.id())
                    .update();
        }
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
