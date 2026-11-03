#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/init.py
# Bind-mounted signing keys stay mode 0600; map the container to their owner.
export LOCAL_UID="$(id -u)" LOCAL_GID="$(id -g)"
if [[ ! -f .runtime/providers.yml ]]; then printf 'app:\n  federation:\n    enabled: false\n' > .runtime/providers.yml; fi
exec docker compose --env-file .env -p aobp -f infra/compose/compose.yml "$@"
