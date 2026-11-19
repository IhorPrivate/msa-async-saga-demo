package ua.nure.msa.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.msa.common.contracts.ReserveStock;
import ua.nure.msa.common.contracts.StockRejected;
import ua.nure.msa.common.contracts.StockReserved;
import ua.nure.msa.common.messaging.Outbox;
import ua.nure.msa.common.messaging.Topology;

/** Work queue: приймає команду ReserveStock (активний в обох режимах). */
@Component
public class ReserveStockListener {

    private static final Logger log = LoggerFactory.getLogger(ReserveStockListener.class);

    private final InventoryService inventory;
    private final Outbox outbox;

    public ReserveStockListener(InventoryService inventory, Outbox outbox) {
        this.inventory = inventory;
        this.outbox = outbox;
    }

    @RabbitListener(queues = Topology.Q_INVENTORY_RESERVE) // [S1]
    @Transactional // [S4] бізнес-зміна й outbox — в одній транзакції
    public void on(ReserveStock cmd) {
        boolean ok = inventory.reserve(cmd.orderId(), cmd.sku(), cmd.quantity()); // [S1]
        log.info("[inventory] reserve order={} sku={} ok={}", cmd.orderId(), cmd.sku(), ok);
        if (ok) outbox.event(cmd.orderId(), Topology.RK_STOCK_RESERVED, new StockReserved(cmd.orderId())); // [S2][S4]
        else outbox.event(cmd.orderId(), Topology.RK_STOCK_REJECTED, new StockRejected(cmd.orderId(), "insufficient stock")); // [S2][S4]
    }
}
