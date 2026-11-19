#!/usr/bin/env bash
# Єдина команда приймання: збірка кожного тегу + наскрізний прогін сценаріїв.
# Використання: ./scripts/verify.sh [--build-only]
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

BUILD_ONLY=0
case "${1:-}" in
  "") ;;
  --build-only) BUILD_ONLY=1 ;;
  *) echo "Використання: $0 [--build-only]" >&2; exit 2 ;;
esac
TAGS="step-0 step-1 step-2 step-3 step-4 step-5 step-6 step-7"
FINAL_TAG="step-7"
SD="$LIB_DIR"

# ---------- передумови ----------
prereq() {
  local ok=1 v
  command -v git >/dev/null 2>&1 || { fail "Потрібен git"; ok=0; }
  command -v curl >/dev/null 2>&1 || { fail "Потрібен curl"; ok=0; }
  if command -v java >/dev/null 2>&1; then
    v="$(java -version 2>&1 | head -n 1 | sed -E 's/.*"([0-9]+)[."].*/\1/')"
    case "$v" in ''|*[!0-9]*) fail "Не вдалося визначити версію Java"; ok=0 ;;
      *) [ "$v" -ge 21 ] || { fail "Потрібна Java 21+, знайдено $v"; ok=0; } ;; esac
  else fail "Потрібна Java 21+ (команда java не знайдена)"; ok=0; fi
  command -v mvn >/dev/null 2>&1 || { fail "Потрібен Maven (mvn)"; ok=0; }
  if [ "$BUILD_ONLY" -eq 0 ]; then
    if ! docker compose version >/dev/null 2>&1; then fail "Потрібен 'docker compose' (плагін Compose v2)"; ok=0
    elif ! docker info >/dev/null 2>&1; then fail "Демон Docker не запущено або немає прав"; ok=0; fi
  fi
  [ "$ok" -eq 1 ] || exit 2
  git -C "$REPO_ROOT" rev-parse --git-dir >/dev/null 2>&1 || { fail "$REPO_ROOT не є git-репозиторієм"; exit 2; }
  if [ -n "$(git -C "$REPO_ROOT" status --porcelain --untracked-files=no)" ]; then
    fail "Робоче дерево не чисте — закомітьте або приберіть зміни (git status)."; exit 2
  fi
  local t
  for t in $TAGS; do
    git -C "$REPO_ROOT" rev-parse -q --verify "refs/tags/$t" >/dev/null || { fail "Немає git-тегу $t"; exit 2; }
  done
}

# ---------- прибирання ----------
WT_BASE=""
cleanup() {
  local rc=$?
  trap - EXIT
  stop_services >/dev/null 2>&1
  if [ -n "$WT_BASE" ] && [ -d "$WT_BASE" ]; then
    local t
    for t in $TAGS; do
      git -C "$REPO_ROOT" worktree remove --force "$WT_BASE/$t" >/dev/null 2>&1 || true
    done
    git -C "$REPO_ROOT" worktree prune >/dev/null 2>&1 || true
    rm -rf "$WT_BASE"
  fi
  exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

step() { # назва команда... — виконує крок, записує PASS/FAIL
  local name="$1"; shift
  info ""; info "=== $name ==="
  if ( "$@" ); then results_add "$name" PASS ""; return 0
  else results_add "$name" FAIL "див. вивід вище"; return 1; fi
}

prereq
WT_BASE="$(mktemp -d "${TMPDIR:-/tmp}/msa-verify.XXXXXX")"

# ---------- (a) збірка кожного тегу ----------
build_tag() {
  local t="$1" dir="$WT_BASE/$1"
  git -C "$REPO_ROOT" worktree add --detach "$dir" "$t" >/dev/null 2>&1 || { fail "worktree для $t"; return 1; }
  if (cd "$dir" && mvn -q -DskipTests package >"$WT_BASE/$t.log" 2>&1); then return 0; fi
  tail -n 40 "$WT_BASE/$t.log" >&2
  return 1
}
FINAL_BUILT=0
for t in $TAGS; do
  if step "збірка $t" build_tag "$t"; then [ "$t" = "$FINAL_TAG" ] && FINAL_BUILT=1; fi
done

# ---------- (b) наскрізні сценарії на фінальному теґу ----------
if [ "$BUILD_ONLY" -eq 0 ]; then
  if [ "$FINAL_BUILT" -ne 1 ]; then
    results_add "e2e на $FINAL_TAG" FAIL "пропущено: збірка $FINAL_TAG провалена"
  else
    export SERVICES_DIR="$WT_BASE/$FINAL_TAG"
    run_mode() { # режим smoke-аргумент
      local mode="$1"; shift
      step "reset ($mode)" "$SD/reset.sh" || return 0
      step "старт сервісів ($mode)" start_services "$mode" || { stop_services; return 0; }
      step "smoke ($mode)" "$SD/smoke.sh" "$@"
      if [ "$mode" = "choreography" ]; then
        step "duplicate (ідемпотентність)" "$SD/duplicate.sh"
        step "poison -> DLQ" "$SD/poison.sh"
        step "outbox при вимкненому брокері" "$SD/outbox-broker-down.sh"
      fi
      stop_services
    }
    run_mode choreography choreography
    run_mode orchestration orchestration
  fi
fi

# ---------- підсумок ----------
results_print
failed="$(results_failed)"
echo
if [ "$failed" -eq 0 ]; then pass "verify: усе пройдено"; exit 0; fi
fail "verify: провалено кроків: $failed"
[ "$BUILD_ONLY" -eq 0 ] && info "Логи сервісів: $RUN_DIR/*.log. Інфраструктуру Docker залишено запущеною."
exit 1
