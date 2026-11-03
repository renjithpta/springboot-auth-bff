#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -B verify
npm --prefix code/ui ci --ignore-scripts
npm --prefix code/ui run build
