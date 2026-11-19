#!/usr/bin/env bash
# Outbox (step-4+): брокер зупинено, замовлення все одно приймається, подія не губиться.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need_cmd curl; need_cmd docker

restore() { docker start shop-rabbitmq >/dev/null 2>&1 || true; }
trap restore EXIT

info "Зупиняю shop-rabbitmq..."
docker stop shop-rabbitmq >/dev/null

out="$(post_order COFFEE 1 100)" || die "order-service недоступний"
code="$(printf '%s\n' "$out" | tail -n 1)"
body="$(printf '%s\n' "$out" | sed '$d')"
[ "$code" = "202" ] || die "POST /orders при вимкненому брокері: HTTP $code (очікували 202)"
id="$(printf '%s' "$body" | sed -n 's/.*"orderId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
[ -n "$id" ] || die "Немає orderId у відповіді: $body"
pass "202 Accepted, orderId=$id (брокер вимкнено)"

sleep 1
info "Неопубліковані рядки outbox у базі orders:"
psql_q orders "SELECT id, routing_key, created_at, published_at FROM outbox WHERE published_at IS NULL ORDER BY created_at"
pending="$(psql_q orders "SELECT count(*) FROM outbox WHERE published_at IS NULL AND aggregate_id = '$id'")"
[ "$pending" -ge 1 ] || die "Рядка outbox для замовлення $id немає — подію не збережено атомарно з замовленням"
pass "Подія чекає в outbox (published_at IS NULL)"

info "Запускаю shop-rabbitmq і чекаю на healthy..."
docker start shop-rabbitmq >/dev/null
for ((i = 0; i < 90; i++)); do
  [ "$(docker inspect -f '{{.State.Health.Status}}' shop-rabbitmq 2>/dev/null || true)" = "healthy" ] && break
  sleep 1
done
[ "$(docker inspect -f '{{.State.Health.Status}}' shop-rabbitmq)" = "healthy" ] || die "RabbitMQ не став healthy за 90 с"

wait_not_status "$id" PENDING 60 || die "Подію не доставлено після відновлення брокера"
pass "Замовлення залишило PENDING -> $LAST_STATUS"
left="$(psql_q orders "SELECT count(*) FROM outbox WHERE published_at IS NULL AND aggregate_id = '$id'")"
[ "$left" = "0" ] || die "Рядок outbox для $id усе ще неопублікований"
pass "Рядок outbox позначено опублікованим"
