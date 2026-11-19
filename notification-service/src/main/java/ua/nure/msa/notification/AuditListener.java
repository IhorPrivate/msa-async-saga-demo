package ua.nure.msa.notification;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Аудит: бачить усі події з shop.events (binding #) і пише їх у event_log. */
@Component // [S2]
public class AuditListener {

    static final String QUEUE = "notification.audit";

    private static final Logger log = LoggerFactory.getLogger(AuditListener.class);

    private final JdbcClient jdbc;

    public AuditListener(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @RabbitListener(queues = QUEUE) // [S2]
    public void on(Message message) { // [S2] сирий Message: тіло лишається JSON-рядком, як воно прийшло
        String key = message.getMessageProperties().getReceivedRoutingKey();
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        jdbc.sql("INSERT INTO event_log (source, routing_key, payload) VALUES ('rabbit', :k, :p)")
                .param("k", key).param("p", json).update();
        log.info("[audit] {} {}", key, json);
    }
}
