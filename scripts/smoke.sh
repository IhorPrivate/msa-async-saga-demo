#!/usr/bin/env bash
# Димові сценарії саги. Передумова: інфраструктура й 4 сервіси працюють.
# $1 — режим саги (лише для виводу), типово choreography.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
MODE="${1:-choreography}"
need_cmd curl; need_cmd docker
info "== smoke: режим $MODE =="

scenario_happy() {
  local before id
  before="$(stock COFFEE)" || return 1
  id="$(create_order COFFEE 2 250)" || return 1
  wait_status "$id" COMPLETED 30 || return 1
  wait_stock COFFEE $((before - 2)) 10 || return 1
  SC_DETAIL="залишок $before -> $((before - 2))"
}
scenario_payment_refused() {
  local before id
  before="$(stock COFFEE)" || return 1
  id="$(create_order COFFEE 1 5000)" || return 1
  wait_status "$id" CANCELLED 30 || return 1
  wait_stock COFFEE "$before" 10 || return 1
  SC_DETAIL="залишок повернувся до $before"
}
scenario_out_of_stock() {
  local id n
  id="$(create_order TEA 1 100)" || return 1
  wait_status "$id" CANCELLED 30 || return 1
  n="$(psql_q payments "SELECT count(*) FROM payments WHERE order_id = '$id' AND status = 'COMPLETED'")" || return 1
  if [ "$n" != "0" ]; then fail "Знайдено $n завершених платежів для замовлення $id"; return 1; fi
  SC_DETAIL="CANCELLED, завершених платежів: 0"
}

run() { # назва функція
  SC_DETAIL=""
  info ""; info "-- $1"
  if "$2"; then pass "$1"; results_add "$1" PASS "$SC_DETAIL"
  else fail "$1"; results_add "$1" FAIL "${LAST_STATUS:+останній статус: $LAST_STATUS}"; fi
}

run "Щасливий шлях (COFFEE, 2, 250)"          scenario_happy
run "Оплату відхилено (COFFEE, 1, 5000)"      scenario_payment_refused
run "Немає на складі (TEA, 1, 100)"           scenario_out_of_stock

results_print
failed="$(results_failed)"
if [ "$failed" -ne 0 ]; then fail "smoke ($MODE): провалено сценаріїв: $failed"; exit 1; fi
pass "smoke ($MODE): усі сценарії пройдено"
