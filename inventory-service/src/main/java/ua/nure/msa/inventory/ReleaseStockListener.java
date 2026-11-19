package ua.nure.msa.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.msa.common.contracts.ReleaseStock;
import ua.nure.msa.common.contracts.StockReleased;
import ua.nure.msa.common.messaging.Inbox;
import ua.nure.msa.common.messaging.OrchestrationMode;
import ua.nure.msa.common.messaging.Outbox;
import ua.nure.msa.common.messaging.Topology;

/** Оркестрація: виконує команду компенсації ReleaseStock. */
@Component
@OrchestrationMode
public class ReleaseStockListener {

    private static final Logger log = LoggerFactory.getLogger(ReleaseStockListener.class);

    private final InventoryService inventory;
    private final Outbox outbox;
    private final Inbox inbox;

    public ReleaseStockListener(InventoryService inventory, Outbox outbox, Inbox inbox) {
        this.inventory = inventory;
        this.outbox = outbox;
        this.inbox = inbox;
    }

    @RabbitListener(queues = Topology.Q_INVENTORY_RELEASE)
    @Transactional
    public void on(ReleaseStock cmd, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
        if (!inbox.firstTime(messageId, Topology.Q_INVENTORY_RELEASE)) return;
        inventory.release(cmd.orderId());
        log.info("[inventory] release order={}", cmd.orderId());
        outbox.event(cmd.orderId(), Topology.RK_STOCK_RELEASED, new StockReleased(cmd.orderId()));
    }
}
