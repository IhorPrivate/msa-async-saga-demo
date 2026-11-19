package ua.nure.msa.notification;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Аналітика: читає журнал подій з Kafka й рахує події за типом. */
@Component // [S3]
public class AnalyticsListener {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsListener.class);

    private final ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();

    @KafkaListener(topics = "shop.events", groupId = "analytics") // [S3] нова група = replay з початку
    public void on(ConsumerRecord<String, String> record) { // [S3]
        var header = record.headers().lastHeader("type"); // routing key події
        // Без заголовка (напр. запис з ранніх кроків) не падаємо: інакше Kafka повторюватиме запис
        String type = header == null ? "unknown" : new String(header.value(), StandardCharsets.UTF_8);
        counts.computeIfAbsent(type, t -> new LongAdder()).increment();
        log.info("[analytics] partition={} offset={} key={} type={}", record.partition(), record.offset(), record.key(), type);
        log.info("[analytics] counts={}", counts);
    }
}
