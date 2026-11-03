#!/usr/bin/env python3
"""Checks repository deployment structure without pretending to run a Docker daemon or Kubernetes cluster."""
from pathlib import Path
import yaml

r = Path(__file__).resolve().parents[1]
for p in r.glob("**/*.yml"):
    if any(x in p.parts for x in ["target", "node_modules", ".runtime"]):
        continue
    list(yaml.safe_load_all(p.read_text()))
objects = list(yaml.safe_load_all((r / "infra/k8s/app.yaml").read_text()))
names = {o["metadata"]["name"] for o in objects if o["kind"] == "Service"}
assert names == {"gateway", "ui", "bff", "auth-server", "flight-service", "redis"}
for o in objects:
    if o["kind"] == "Deployment":
        assert o["spec"]["template"]["spec"]["securityContext"]["runAsNonRoot"]
        assert o["spec"]["template"]["spec"]["enableServiceLinks"] is False
        for c in o["spec"]["template"]["spec"]["containers"]:
            assert "resources" in c
print("PASS YAML parsing, service inventory and deployment security structure")
