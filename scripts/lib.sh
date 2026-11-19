#!/usr/bin/env bash
# Спільні функції для демо-скриптів (підключається через `source`).
# Виклики: у скриптах-викликачах потрібні `set -euo pipefail`.

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$LIB_DIR/.." && pwd)"
# Звідки брати jar-файли (verify.sh підставляє тимчасовий worktree)
SERVICES_DIR="${SERVICES_DIR:-$REPO_ROOT}"
RUN_DIR="${RUN_DIR:-$REPO_ROOT/.run}"
ORDER_URL="${ORDER_URL:-http://localhost:8081}"
RMQ_API="${RMQ_API:-http://localhost:15672/api}"
RMQ_AUTH="${RMQ_AUTH:-guest:guest}"
SERVICES="order-service:8081 inventory-service:8082 payment-service:8083 notification-service:8084"

# ---------- кольори й повідомлення ----------
if [ -t 1 ]; then
  C_RED=$'\033[31m'; C_GRN=$'\033[32m'; C_YEL=$'\033[33m'; C_RST=$'\033[0m'
else
  C_RED=""; C_GRN=""; C_YEL=""; C_RST=""
fi
info() { printf '%s\n' "$*"; }
warn() { printf '%s%s%s\n' "$C_YEL" "$*" "$C_RST"; }
pass() { printf '%sPASS%s %s\n' "$C_GRN" "$C_RST" "$*"; }
fail() { printf '%sFAIL%s %s\n' "$C_RED" "$C_RST" "$*" >&2; }
die()  { fail "$*"; exit 1; }

# ---------- таблиця результатів ----------
RES_NAMES=(); RES_STATUS=(); RES_DETAIL=()
results_add() { RES_NAMES+=("$1"); RES_STATUS+=("$2"); RES_DETAIL+=("${3:-}"); }
results_failed() {
  local n=0 s
  for s in "${RES_STATUS[@]+"${RES_STATUS[@]}"}"; do [ "$s" = "PASS" ] || n=$((n + 1)); done
  echo "$n"
}
results_print() {
  local i s col
  printf '\n%-44s %-6s %s\n' "СЦЕНАРІЙ" "ІТОГ" "ДЕТАЛІ"
  printf '%s\n' "--------------------------------------------------------------------------------"
  for i in "${!RES_NAMES[@]}"; do
    s="${RES_STATUS[$i]}"
    if [ "$s" = "PASS" ]; then col="$C_GRN"; else col="$C_RED"; fi
    printf '%-44s %s%-6s%s %s\n' "${RES_NAMES[$i]}" "$col" "$s" "$C_RST" "${RES_DETAIL[$i]}"
  done
}

# ---------- вимоги ----------
need_cmd() { command -v "$1" >/dev/null 2>&1 || die "Не знайдено команду '$1' — встановіть її."; }

# ---------- PostgreSQL ----------
psql_q() { # db sql
  docker exec shop-postgres psql -U shop -d "$1" -tAc "$2"
}
stock() { # sku -> доступний залишок
  psql_q inventory "SELECT available FROM stock WHERE sku = '$1'"
}

# ---------- REST order-service ----------
post_order() { # sku qty amount -> тіло відповіді, останній рядок = HTTP-код
  curl -sS -w '\n%{http_code}' -X POST "$ORDER_URL/orders" \
    -H 'Content-Type: application/json' \
    -d "{\"sku\":\"$1\",\"quantity\":$2,\"amount\":$3}"
}
create_order() { # sku qty amount -> друкує orderId
  local out code body id
  out="$(post_order "$1" "$2" "$3")" || { fail "order-service недоступний на $ORDER_URL"; return 1; }
  code="$(printf '%s\n' "$out" | tail -n 1)"
  body="$(printf '%s\n' "$out" | sed '$d')"
  if [ "$code" != "202" ]; then
    fail "POST /orders повернув HTTP $code (очікували 202): $body"; return 1
  fi
  id="$(printf '%s' "$body" | sed -n 's/.*"orderId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  [ -n "$id" ] || { fail "Не вдалося розібрати orderId з відповіді: $body"; return 1; }
  printf '%s\n' "$id"
}
order_status() { # orderId -> статус (порожньо, якщо немає)
  curl -s "$ORDER_URL/orders/$1" \
    | sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
}
LAST_STATUS=""
wait_status() { # orderId expected timeoutSec
  local id="$1" want="$2" t="${3:-30}" i max
  max=$((t * 2)); LAST_STATUS=""
  for ((i = 0; i < max; i++)); do
    LAST_STATUS="$(order_status "$id" || true)"
    [ "$LAST_STATUS" = "$want" ] && return 0
    sleep 0.5
  done
  fail "Замовлення $id: очікували $want за ${t}с, останній статус: '${LAST_STATUS:-<немає>}'"
  return 1
}
wait_not_status() { # orderId unwanted timeoutSec — чекає, поки статус зміниться
  local id="$1" bad="$2" t="${3:-30}" i max
  max=$((t * 2)); LAST_STATUS=""
  for ((i = 0; i < max; i++)); do
    LAST_STATUS="$(order_status "$id" || true)"
    if [ -n "$LAST_STATUS" ] && [ "$LAST_STATUS" != "$bad" ]; then return 0; fi
    sleep 0.5
  done
  fail "Замовлення $id лишилось у статусі '${LAST_STATUS:-<немає>}' після ${t}с"
  return 1
}
wait_stock() { # sku expected timeoutSec
  local i max=$(( ${3:-10} * 2 )) cur=""
  for ((i = 0; i < max; i++)); do
    cur="$(stock "$1" || true)"
    [ "$cur" = "$2" ] && return 0
    sleep 0.5
  done
  fail "Залишок $1: очікували $2, фактично '${cur:-<немає>}'"
  return 1
}

# ---------- RabbitMQ management API ----------
json_escape() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }
new_uuid() {
  if [ -r /proc/sys/kernel/random/uuid ]; then cat /proc/sys/kernel/random/uuid
  else uuidgen | tr 'A-Z' 'a-z'; fi
}
# Публікація в чергу через default exchange; routing key = ім'я черги.
rmq_publish() { # queue message_id payload_text
  local body resp
  body="{\"properties\":{\"message_id\":\"$(json_escape "$2")\",\"content_type\":\"application/json\",\"delivery_mode\":2},\"routing_key\":\"$(json_escape "$1")\",\"payload\":\"$(json_escape "$3")\",\"payload_encoding\":\"string\"}"
  resp="$(curl -sS -u "$RMQ_AUTH" -X PUT -H 'Content-Type: application/json' \
    -d "$body" "$RMQ_API/exchanges/%2F/amq.default/publish")" || { fail "RabbitMQ management API недоступний"; return 1; }
  printf '%s' "$resp" | grep -Eq '"routed"[[:space:]]*:[[:space:]]*true' \
    || { fail "Повідомлення не маршрутизовано (черга '$1' існує?): $resp"; return 1; }
}
queue_messages() { # queue -> кількість повідомлень (messages)
  curl -sS -u "$RMQ_AUTH" "$RMQ_API/queues/%2F/$1" | grep -o '"messages":[0-9]*' | head -n 1 | cut -d: -f2
}

# ---------- сервіси ----------
port_answers() { curl -s -o /dev/null --max-time 2 "http://localhost:$1/"; }

start_services() { # [mode]
  local mode="${1:-choreography}" entry name port jar pid i
  case "$mode" in choreography|orchestration) ;; *) die "Невідомий режим саги: $mode";; esac
  need_cmd java; need_cmd curl
  mkdir -p "$RUN_DIR"
  for entry in $SERVICES; do
    port="${entry#*:}"
    if port_answers "$port"; then die "Порт $port вже зайнятий — зупиніть інший екземпляр (див. README)."; fi
  done
  for entry in $SERVICES; do
    name="${entry%%:*}"; port="${entry#*:}"
    jar="$SERVICES_DIR/$name/target/$name-1.0.0.jar"
    [ -f "$jar" ] || die "Немає $jar — виконайте 'mvn -DskipTests package'."
    nohup java -jar "$jar" "--saga.mode=$mode" >"$RUN_DIR/$name.log" 2>&1 &
    echo $! >"$RUN_DIR/$name.pid"
  done
  info "Запущено 4 сервіси (saga.mode=$mode), логи: $RUN_DIR/*.log"
  for entry in $SERVICES; do
    name="${entry%%:*}"; port="${entry#*:}"
    pid="$(cat "$RUN_DIR/$name.pid")"
    for ((i = 0; i < 240; i++)); do
      port_answers "$port" && break
      if ! kill -0 "$pid" 2>/dev/null; then
        tail -n 20 "$RUN_DIR/$name.log" >&2 || true
        die "$name завершився під час старту (див. $RUN_DIR/$name.log)"
      fi
      sleep 0.5
    done
    port_answers "$port" || die "$name не відповів на порту $port за 120 с"
    pass "$name слухає порт $port"
  done
}

stop_services() {
  local f pid i
  [ -d "$RUN_DIR" ] || return 0
  for f in "$RUN_DIR"/*.pid; do
    [ -e "$f" ] || continue
    pid="$(cat "$f")"
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      for ((i = 0; i < 20; i++)); do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
      kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null
    fi
    rm -f "$f"
  done
  return 0
}
