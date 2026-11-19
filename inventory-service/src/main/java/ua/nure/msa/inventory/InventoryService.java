package ua.nure.msa.inventory;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Бізнес-логіка складу: резерв і зняття резерву. */
@Service
public class InventoryService {

    private record Reserved(String sku, int quantity) {}

    private final JdbcClient jdbc;

    public InventoryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Навмисно НЕ ідемпотентний: повторний виклик спише залишок вдруге (демо S5). */
    public boolean reserve(UUID orderId, String sku, int quantity) {
        int updated = jdbc.sql("UPDATE stock SET available = available - :q WHERE sku = :sku AND available >= :q")
                .param("q", quantity).param("sku", sku).update();
        if (updated == 0) {
            return false;
        }
        jdbc.sql("INSERT INTO reservations (id, order_id, sku, quantity, status) VALUES (:id, :o, :sku, :q, 'RESERVED')")
                .param("id", UUID.randomUUID()).param("o", orderId).param("sku", sku).param("q", quantity).update();
        return true;
    }

    /** Безпечно викликати двічі: зняті резерви вже не RESERVED. */
    public void release(UUID orderId) {
        // UPDATE ... RETURNING блокує рядки, тож два паралельні release не повернуть залишок двічі
        var released = jdbc.sql("UPDATE reservations SET status = 'RELEASED' WHERE order_id = :o AND status = 'RESERVED' RETURNING sku, quantity")
                .param("o", orderId)
                .query((rs, n) -> new Reserved(rs.getString("sku"), rs.getInt("quantity")))
                .list();
        released.forEach(r -> jdbc.sql("UPDATE stock SET available = available + :q WHERE sku = :sku")
                .param("q", r.quantity()).param("sku", r.sku()).update());
    }
}
