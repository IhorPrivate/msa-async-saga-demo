#!/usr/bin/env bash
# Poison message: невалідний JSON у inventory.reserve має опинитись у shop.dlq.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need_cmd curl

# Лічильники в management API оновлюються раз на ~5 с — даємо їм відстояться
sleep 6
base="$(queue_messages shop.dlq)"
[ -n "$base" ] || die "Не вдалося прочитати чергу shop.dlq (RabbitMQ запущено, черга створена?)"
info "shop.dlq до: $base"

rmq_publish inventory.reserve "$(new_uuid)" '{ це не JSON'
info "Опубліковано невалідне повідомлення; чекаю на повтори та DLQ..."

cur="$base"
for ((i = 0; i < 60; i++)); do
  cur="$(queue_messages shop.dlq || true)"
  [ -n "$cur" ] && [ "$cur" -ge $((base + 1)) ] && break
  sleep 1
done
sleep 6
cur="$(queue_messages shop.dlq)"
info "shop.dlq після: $cur"
if [ "$cur" -eq $((base + 1)) ]; then pass "Poison message у shop.dlq (+1)"
else die "shop.dlq: очікували $((base + 1)), фактично $cur"; fi
