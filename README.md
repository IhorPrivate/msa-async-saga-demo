# msa-async-saga-demo

Навчальне демо до лекції «Асинхронна взаємодія та розподілені транзакції» (НУРЕ, МАтР).

**Що показує.** Чотири сервіси Spring Boot 4.1 (Java 21) реалізують сагу «замовлення → резерв товару → оплата».
RabbitMQ несе команди та операційні події (work queue, pub/sub, DLQ), Kafka зберігає журнал подій для аналітики й replay,
PostgreSQL дає по базі кожному сервісу. Далі додаються transactional outbox та inbox (ідемпотентність), а сага
працює у двох режимах: хореографія (за замовчуванням) і оркестрація (`saga.mode=orchestration`).

```
POST /orders          RabbitMQ (shop.events)                         Kafka
 client -> order-service --order.created--> inventory-service --stock.reserved--> payment-service
              ^                                  ^   | stock.rejected                |
              |                                  |   v                               |
              |<------- stock.reserved / stock.rejected / payment.completed ---------+
              |                                  |<-------- payment.failed (компенсація: release)
              v                                  
        COMPLETED | CANCELLED         notification-service: [audit] (#) та [analytics] (Kafka shop.events)
```

> **Увага.** Автори не компілювали цей код власним інструментарієм (Maven Central та Docker були недоступні).
> Приймальна перевірка — `./scripts/verify.sh`: збирає кожен тег і проганяє всі сценарії на реальних брокерах.

## Вимоги
Java 21+, Maven 3.9+, Docker з Compose v2, curl, git. Вільні порти: 5432, 5672, 15672, 9092, 8081-8084.

## Швидкий старт
```bash
docker compose up -d --wait          # RabbitMQ, Kafka, PostgreSQL
mvn -DskipTests install              # збирає всі модулі; install — щоб -pl нижче знайшов common
mvn -pl order-service spring-boot:run        # у окремих терміналах:
mvn -pl inventory-service spring-boot:run    #   inventory-service
mvn -pl payment-service spring-boot:run      #   payment-service
mvn -pl notification-service spring-boot:run #   notification-service
```
З IntelliJ: запустіть чотири класи `*Application` (порти 8081-8084). Приклади запитів — `requests.http`.

## Кроки та теги
| Тег | Що додається |
|---|---|
| `step-0` | стартер: інфраструктура, бізнес-логіка, REST, схеми, скелети з `TODO`, вимкнені класи оркестрації |
| `step-1` | work queue: `inventory.reserve`, `ReserveStockListener`, конкуруючі споживачі |
| `step-2` | pub/sub у `shop.events`, `AuditListener`, poison message -> DLQ |
| `step-3` | Kafka: дублювання подій у топік, `AnalyticsListener`, replay |
| `step-4` | transactional outbox: `Outbox` + `OutboxRelay` |
| `step-5` | inbox: ідемпотентний споживач |
| `step-6` | хореографія саги |
| `step-7` | оркестрація (`--saga.mode=orchestration`) |

Перейти на крок: `git checkout step-N` (потім `mvn -DskipTests install`). До `step-6` замовлення не завершується
повністю; на `step-0` воно лишається `PENDING`.

## Конкуруючі споживачі (step-1)
Другий екземпляр inventory:
```bash
java -jar inventory-service/target/inventory-service-1.0.0.jar --server.port=8092
# або: mvn -pl inventory-service spring-boot:run -Dspring-boot.run.arguments=--server.port=8092
```
Створіть кілька замовлень — у логах видно, що повідомлення розподіляються між екземплярами.

## Скрипти (`./scripts`)
- `verify.sh` — головна перевірка: збірка `step-0..7` + усі сценарії в обох режимах (`--build-only` — лише збірка).
- `reset.sh` — `docker compose down -v` і `up -d --wait`: чисті брокери й бази.
- `smoke.sh [режим]` — три сценарії саги (успіх, відмова оплати з компенсацією, немає на складі).
- `duplicate.sh` — той самий `ReserveStock` двічі з одним `message_id`: залишок зменшується на 1 (step-5+).
- `poison.sh` — невалідний JSON у `inventory.reserve` потрапляє в `shop.dlq`.
- `outbox-broker-down.sh` — зупиняє RabbitMQ, замовлення приймається (202), подія чекає в outbox і доходить після старту (step-4+).
- `kafka-replay.sh` — друкує весь `shop.events` з початку новою consumer group (ключ, партиція, offset).
- `lib.sh` — спільні функції (підключається іншими скриптами).

Скрипти `smoke/duplicate/poison/outbox-broker-down` припускають, що інфраструктура й сервіси вже працюють;
`verify.sh` запускає сервіси сам (логи й pid у `.run/`).

## Перемикання режиму саги
```bash
./scripts/reset.sh                       # чисті черги й бази (топологія різна в режимах)
# перезапустіть сервіси з аргументом:
java -jar order-service/target/order-service-1.0.0.jar --saga.mode=orchestration
# (так само для решти сервісів; в IntelliJ — Program arguments: --saga.mode=orchestration)
./scripts/smoke.sh orchestration
```

## Де дивитись
- RabbitMQ UI: http://localhost:15672 (guest/guest) — черги, exchange `shop.events`, `shop.dlq`.
- Логи notification-service: префікс `[audit]` (усі події з RabbitMQ) і `[analytics]` (Kafka, група `analytics`).
- Бази: `docker exec -it shop-postgres psql -U shop -d orders` (також `inventory`, `payments`, `notifications`).

## Типові проблеми
- **Порт зайнятий** (8081-8084, 5432, 5672, 9092): зупиніть попередній процес або контейнер; `verify.sh` та `start_services` відмовляються стартувати на зайнятому порту.
- **Застарілі черги після зміни режиму**: виконайте `./scripts/reset.sh` і перезапустіть сервіси.
- **Kafka ще не готова**: `docker compose up -d --wait` чекає на healthcheck; якщо сервіс стартував раніше, перезапустіть його. Перевірка: `docker compose ps`.
- **`verify.sh` відмовляє через «брудне» дерево**: закомітьте зміни або `git stash` (ігноруються лише неушкоджені untracked-файли).
