#!/usr/bin/env bash
# Replay: читаємо весь топік shop.events з початку новою consumer group.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need_cmd docker
BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
GROUP="replay-$(date +%s)"
mkdir -p "$RUN_DIR"
info "Топік shop.events з початку; нова група: $GROUP (формат: partition | offset | key | value)"
# Kafka 4.2+ (KIP-1147): --property застаріла, замість неї --formatter-property
# Після --timeout-ms консюмер завершується з помилкою TimeoutException — це нормально
docker exec shop-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server "$BOOTSTRAP" --topic shop.events --from-beginning \
  --group "$GROUP" --timeout-ms 10000 \
  --formatter-property print.partition=true --formatter-property print.offset=true \
  --formatter-property print.key=true --formatter-property key.separator=' | ' \
  2>"$RUN_DIR/kafka-replay.err" | tee "$RUN_DIR/kafka-replay.out" || true
n="$(wc -l <"$RUN_DIR/kafka-replay.out" | tr -d ' ')"
if [ "$n" -gt 0 ]; then pass "Прочитано подій: $n (журнал можна перечитати скільки завгодно)"
else fail "Подій не прочитано"; cat "$RUN_DIR/kafka-replay.err" >&2; exit 1; fi
