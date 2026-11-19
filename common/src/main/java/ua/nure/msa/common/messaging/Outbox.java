package ua.nure.msa.common.messaging;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Патерн Transactional Outbox: повідомлення пишемо в таблицю в тій самій транзакції, що й зміну даних. */
@Component
public class Outbox {

    private final JdbcClient jdbc;
    private final JsonMapper mapper;

    public Outbox(JdbcClient jdbc, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    // MANDATORY: без чужої транзакції outbox втрачає сенс (запис мав би комітитись окремо від бізнес-даних)
    @Transactional(propagation = Propagation.MANDATORY)
    public void event(UUID aggregateId, String routingKey, Object payload) { // [S4]
        insert(aggregateId, Topology.EVENTS_EXCHANGE, routingKey, payload);
    }

    // Команда йде через default exchange: routing key = ім'я черги
    @Transactional(propagation = Propagation.MANDATORY)
    public void command(UUID aggregateId, String queue, Object payload) { // [S4]
        insert(aggregateId, "", queue, payload);
    }

    private void insert(UUID aggregateId, String exchange, String routingKey, Object payload) {
        // Jackson 3: JacksonException unchecked, тож при помилці серіалізації транзакція відкотиться
        String json = mapper.writeValueAsString(payload);
        jdbc.sql("INSERT INTO outbox(id, aggregate_id, exchange, routing_key, payload) VALUES (?, ?, ?, ?, ?)")
                .param(UUID.randomUUID())
                .param(aggregateId)
                .param(exchange)
                .param(routingKey)
                .param(json)
                .update();
    }
}
