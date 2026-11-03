# Run with plain Kubernetes YAML

## Prerequisites

A development Kubernetes cluster, configured kubectl, Docker image builder, a working default StorageClass, and a CNI enforcing NetworkPolicy. Use a local cluster such as kind or minikube for the supplied localhost callback configuration. Production ingress/TLS, external secrets, HA data services and registry policy require environment-specific configuration.

## Step 1 — build and make images available

```bash
scripts/k8s.sh build
```

For kind (replace the cluster name if needed):

```bash
kind load docker-image --name kind aobp/auth-server:local aobp/bff:local aobp/flight-service:local aobp/gateway:local aobp/ui:local
```

For a remote cluster, push these images to your approved registry and edit the image references in `infra/k8s/app.yaml`. `IfNotPresent` is suitable for this local demo; use immutable image digests for controlled releases.

## Step 2 — create secrets and deploy

```bash
scripts/k8s.sh deploy
kubectl -n aobp get pods,services,pvc
```

The script initializes local secrets, creates Kubernetes Secrets from files, and applies plain manifests. No live credentials are committed. `secret.example.yaml` is documentation only; do not apply it with placeholders. Kubernetes Secret encoding is not encryption; enable cluster encryption at rest and least-privilege RBAC.

Expected: two BFF pods, one instance of each other service, and bound auth/Redis PVCs. The identity server deliberately remains single replica because its demo H2 file database and HTTP sessions are not cluster-ready.

## Step 3 — expose the two browser-facing endpoints

```bash
scripts/k8s.sh forward
```

Keep this terminal running. In a second terminal:

```bash
curl --fail http://localhost:8080/bff/api/v1/session
curl --fail http://127.0.0.1:9000/.well-known/openid-configuration
python3 scripts/smoke.py
```

Open http://localhost:8080. Both identity and app endpoints are necessary; forwarding only the gateway is insufficient for OIDC browser redirects. Port-forward selects one gateway pod, which still routes requests to the BFF Service and shared Redis sessions.

## Step 4 — inspect and stop

```bash
kubectl -n aobp logs deployment/bff --all-pods=true --tail=100
kubectl -n aobp describe pod <pod-name>
scripts/k8s.sh stop
```

Stop scales deployments to zero without deleting secrets or PVCs. Restore the topology with deploy. Deleting namespace `aobp` destroys its namespaced resources and may delete persistent data; do that only when intentionally discarding the demo.

## Production adaptation

Replace public issuer/callback/home URLs together with approved HTTPS addresses; set COOKIE_SECURE=true. Route identity endpoints to the auth server and app/BFF paths through the gateway. Add external database and HA Redis, shared identity-server sessions, key rotation, encrypted backups, ingress limits, workload identity/secret delivery and cluster policy. Coordinate the shared BFF encryption key across replicas; rotating it requires a session/token migration or controlled logout.
