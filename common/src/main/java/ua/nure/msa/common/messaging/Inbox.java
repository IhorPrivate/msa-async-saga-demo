package ua.nure.msa.common.messaging;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Патерн Idempotent Consumer (Inbox): запам'ятовуємо оброблені message_id, щоб дубль не виконався двічі. */
@Component
public class Inbox {

    private final JdbcClient jdbc;

    public Inbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // MANDATORY: запис у inbox і бізнес-зміна мають комітитись разом, інакше дедуплікація не працює
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(String messageId, String consumer) { // [S5]
        int inserted = jdbc.sql("INSERT INTO processed_messages(message_id, consumer) VALUES (?, ?) ON CONFLICT DO NOTHING")
                .param(messageId)
                .param(consumer)
                .update();
        return inserted == 1;
    }
}
