package ua.nure.msa.inventory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import ua.nure.msa.common.contracts.ReserveStock;
import ua.nure.msa.common.contracts.StockRejected;
import ua.nure.msa.common.contracts.StockReserved;
import ua.nure.msa.common.messaging.Topology;

/** Work queue: приймає команду ReserveStock (активний в обох режимах). */
@Component
public class ReserveStockListener {

    private static final Logger log = LoggerFactory.getLogger(ReserveStockListener.class);

    private final InventoryService inventory;
    private final RabbitTemplate rabbit;
    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper mapper;

    public ReserveStockListener(InventoryService inventory, RabbitTemplate rabbit,
                                KafkaTemplate<String, String> kafka, JsonMapper mapper) {
        this.inventory = inventory;
        this.rabbit = rabbit;
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @RabbitListener(queues = Topology.Q_INVENTORY_RESERVE) // [S1]
    @Transactional
    public void on(ReserveStock cmd) {
        boolean ok = inventory.reserve(cmd.orderId(), cmd.sku(), cmd.quantity()); // [S1]
        log.info("[inventory] reserve order={} sku={} ok={}", cmd.orderId(), cmd.sku(), ok);
        if (ok) publish(cmd.orderId(), Topology.RK_STOCK_RESERVED, new StockReserved(cmd.orderId())); // [S2]
        else publish(cmd.orderId(), Topology.RK_STOCK_REJECTED, new StockRejected(cmd.orderId(), "insufficient stock")); // [S2]
    }

    private void publish(UUID orderId, String routingKey, Object event) {
        rabbit.convertAndSend(Topology.EVENTS_EXCHANGE, routingKey, event);
        ProducerRecord<String, String> record = new ProducerRecord<>(
                Topology.KAFKA_EVENTS_TOPIC, orderId.toString(), mapper.writeValueAsString(event));
        record.headers().add("type", routingKey.getBytes(StandardCharsets.UTF_8));
        kafka.send(record); // [S3] ключ = orderId → одна партиція → порядок подій замовлення
    }
}
