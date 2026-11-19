#!/usr/bin/env bash
# Чистий старт інфраструктури: видаляє томи (брокери й бази) і піднімає наново.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need_cmd docker
cd "$REPO_ROOT"
info "Зупиняю контейнери та видаляю томи..."
docker compose down -v
info "Піднімаю інфраструктуру (чекаю на healthcheck)..."
docker compose up -d --wait
pass "Інфраструктура чиста й готова."
warn "Якщо сервіси вже були запущені — перезапустіть їх (вони втратили з'єднання й схему)."
