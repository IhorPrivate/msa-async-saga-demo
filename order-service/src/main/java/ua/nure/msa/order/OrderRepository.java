package ua.nure.msa.order;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Сховище замовлень; guarded-перехід робить обробку подій ідемпотентною. */
@Component
public class OrderRepository {

    private final JdbcClient jdbc;

    public OrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Order o) {
        jdbc.sql("INSERT INTO orders (id, sku, quantity, amount, status) VALUES (?, ?, ?, ?, ?)")
                .param(o.id()).param(o.sku()).param(o.quantity()).param(o.amount()).param(o.status().name())
                .update();
    }

    public Optional<Order> find(UUID id) {
        // Явний RowMapper: не залежимо від того, як маппер за замовчуванням читає enum у record
        return jdbc.sql("SELECT id, sku, quantity, amount, status FROM orders WHERE id = ?")
                .param(id)
                .query((rs, i) -> new Order(rs.getObject("id", UUID.class), rs.getString("sku"),
                        rs.getInt("quantity"), rs.getBigDecimal("amount"),
                        OrderStatus.valueOf(rs.getString("status"))))
                .optional();
    }

    /** true лише якщо рядок справді змінився; дубль або запізніла подія дає false. */
    public boolean transition(UUID id, OrderStatus to, OrderStatus... from) {
        // Плейсхолдери будуємо за кількістю статусів; самі значення йдуть параметрами, не конкатенацією
        String in = Arrays.stream(from).map(s -> "?").collect(Collectors.joining(", "));
        JdbcClient.StatementSpec sql = jdbc
                .sql("UPDATE orders SET status = ? WHERE id = ? AND status IN (" + in + ")")
                .param(to.name()).param(id);
        for (OrderStatus s : from) {
            sql = sql.param(s.name());
        }
        return sql.update() == 1;
    }
}
