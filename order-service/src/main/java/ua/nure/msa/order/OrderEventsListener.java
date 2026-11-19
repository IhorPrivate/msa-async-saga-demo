package ua.nure.msa.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.nure.msa.common.contracts.StockRejected;
import ua.nure.msa.common.contracts.StockReserved;

import static ua.nure.msa.order.OrderStatus.CANCELLED;
import static ua.nure.msa.order.OrderStatus.PENDING;
import static ua.nure.msa.order.OrderStatus.STOCK_RESERVED;

/** Pub/sub: order-service реагує на події складу й сам рухає статус замовлення. */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final OrderRepository orders;

    public OrderEventsListener(OrderRepository orders) {
        this.orders = orders;
    }

    @RabbitListener(queues = "order.on-stock-reserved") // [S2]
    @Transactional
    public void onStockReserved(StockReserved e) {
        boolean moved = orders.transition(e.orderId(), STOCK_RESERVED, PENDING);
        log.info("stock.reserved order={} moved={}", e.orderId(), moved);
    }

    @RabbitListener(queues = "order.on-stock-rejected") // [S2]
    @Transactional
    public void onStockRejected(StockRejected e) {
        boolean moved = orders.transition(e.orderId(), CANCELLED, PENDING);
        log.info("stock.rejected order={} moved={}", e.orderId(), moved);
    }
}
