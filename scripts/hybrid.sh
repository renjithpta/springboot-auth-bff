#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/init.py
case "${1:-start}" in
 start) docker compose --env-file .env -p aobp-infra -f infra/compose/infra.yml up -d --wait
        python3 scripts/native.py start "${@:2}" ;;
 stop) python3 scripts/native.py stop
       docker compose --env-file .env -p aobp-infra -f infra/compose/infra.yml stop ;;
 *) echo 'Usage: scripts/hybrid.sh start [--simulator] | stop'; exit 2 ;;
esac
