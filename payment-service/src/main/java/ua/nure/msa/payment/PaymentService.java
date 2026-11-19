package ua.nure.msa.payment;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Бізнес-логіка оплати: відкриття платежу та списання. */
@Service
public class PaymentService {

    private static final BigDecimal LIMIT = new BigDecimal("1000");

    private record Row(UUID id, BigDecimal amount, String status) {}

    private final JdbcClient jdbc;

    public PaymentService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Ідемпотентно завдяки UNIQUE(order_id) + ON CONFLICT DO NOTHING. */
    public void open(UUID orderId, BigDecimal amount) {
        jdbc.sql("INSERT INTO payments (id, order_id, amount, status) VALUES (:id, :o, :a, 'PENDING') ON CONFLICT (order_id) DO NOTHING")
                .param("id", UUID.randomUUID()).param("o", orderId).param("a", amount).update();
    }

    public PaymentResult charge(UUID orderId) {
        // FOR UPDATE: два паралельні charge одного замовлення не обженуть один одного
        var row = jdbc.sql("SELECT id, amount, status FROM payments WHERE order_id = :o FOR UPDATE")
                .param("o", orderId)
                .query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getBigDecimal("amount"), rs.getString("status")))
                .optional()
                .orElseThrow(() -> new IllegalStateException("payment not opened yet: " + orderId)); // retry слухача покриває гонку з order.created
        if (!row.status().equals("PENDING")) { // повторний виклик — повертаємо збережений результат
            return toResult(row.id(), row.status().equals("COMPLETED"));
        }
        boolean ok = row.amount().compareTo(LIMIT) <= 0;
        jdbc.sql("UPDATE payments SET status = :s WHERE id = :id")
                .param("s", ok ? "COMPLETED" : "FAILED").param("id", row.id()).update();
        return toResult(row.id(), ok);
    }

    private static PaymentResult toResult(UUID id, boolean ok) {
        return new PaymentResult(ok, id, ok ? null : "insufficient funds");
    }
}
