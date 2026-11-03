#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
case "${1:-deploy}" in
 build)
  for module in auth-server bff flight-service gateway; do docker build --build-arg "MODULE=$module" -f infra/docker/Dockerfile.java -t "aobp/$module:local" .; done
  docker build -f infra/docker/Dockerfile.ui -t aobp/ui:local . ;;
 deploy)
  python3 scripts/init.py
  kubectl create namespace aobp --dry-run=client -o yaml | kubectl apply -f -
  kubectl -n aobp create secret generic app-secrets --from-env-file=.env --dry-run=client -o yaml | kubectl apply -f -
  kubectl -n aobp create secret generic signing-key --from-file=signing.pem=.runtime/signing.pem --dry-run=client -o yaml | kubectl apply -f -
  kubectl apply -f infra/k8s/app.yaml
  for name in auth-server redis flight-service bff ui gateway; do kubectl -n aobp rollout status "deployment/$name" --timeout=240s; done ;;
 forward)
  kubectl -n aobp port-forward service/auth-server 9000:9000 --address 127.0.0.1 &
  first=$!
  trap 'kill "$first" 2>/dev/null || true' EXIT INT TERM
  kubectl -n aobp port-forward service/gateway 8080:8080 --address 127.0.0.1 ;;
 stop) kubectl -n aobp scale deployment auth-server bff flight-service gateway ui redis --replicas=0 ;;
 *) echo 'Usage: scripts/k8s.sh build|deploy|forward|stop'; exit 2 ;;
esac
