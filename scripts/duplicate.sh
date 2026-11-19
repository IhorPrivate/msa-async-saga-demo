#!/usr/bin/env bash
# Ідемпотентність (step-5+): той самий ReserveStock з тим самим message_id двічі.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need_cmd curl; need_cmd docker

before="$(stock COFFEE)"
[ "$before" -ge 1 ] || die "Залишок COFFEE = $before; потрібен хоча б 1 (виконайте reset.sh)."
order_id="$(new_uuid)"
msg_id="$(new_uuid)"
payload="{\"orderId\":\"$order_id\",\"sku\":\"COFFEE\",\"quantity\":1}"
info "orderId=$order_id message_id=$msg_id; залишок COFFEE до: $before"

rmq_publish inventory.reserve "$msg_id" "$payload"
rmq_publish inventory.reserve "$msg_id" "$payload"
info "Опубліковано двічі той самий message_id у inventory.reserve."

wait_stock COFFEE $((before - 1)) 15 || die "Перше повідомлення не оброблено (inventory-service запущено?)"
info "Чекаю, щоб дубль точно дійшов до споживача..."
sleep 4
after="$(stock COFFEE)"
rows="$(psql_q inventory "SELECT count(*) FROM reservations WHERE order_id = '$order_id'")"
info "Залишок після: $after; рядків у reservations: $rows"
if [ "$after" -eq $((before - 1)) ] && [ "$rows" = "1" ]; then
  pass "Дубль проігноровано: залишок зменшено рівно на 1"
else
  die "Залишок $before -> $after (очікували $((before - 1))), reservations=$rows: Inbox не спрацював"
fi
