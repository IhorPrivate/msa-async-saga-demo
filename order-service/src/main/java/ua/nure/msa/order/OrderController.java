package ua.nure.msa.order;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** REST-вхід у сагу: приймає замовлення й одразу відповідає 202, не чекаючи результату. */
@RestController
public class OrderController {

    public record CreateOrderRequest(String sku, int quantity, BigDecimal amount) {
    }

    public record CreateOrderResponse(UUID orderId, OrderStatus status) {
    }

    private final OrderService service;
    private final OrderRepository orders;

    public OrderController(OrderService service, OrderRepository orders) {
        this.service = service;
        this.orders = orders;
    }

    @PostMapping("/orders")
    public ResponseEntity<CreateOrderResponse> create(@RequestBody CreateOrderRequest req) {
        Order order = service.create(req.sku(), req.quantity(), req.amount());
        // 202: замовлення прийнято, але сага ще триває асинхронно
        return ResponseEntity.accepted().body(new CreateOrderResponse(order.id(), order.status()));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> get(@PathVariable UUID id) {
        return orders.find(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
