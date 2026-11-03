#!/usr/bin/env python3
"""Starts/stops the sample using Java and a static UI server; Redis is independently managed."""
import argparse, json, os, signal, subprocess, time, urllib.request
from pathlib import Path

root = Path(__file__).resolve().parents[1]
os.chdir(root)
p = argparse.ArgumentParser()
p.add_argument("action", choices=["start", "stop", "status"])
p.add_argument("--simulator", action="store_true")
p.add_argument("--redis", action="store_true")
args = p.parse_args()
runtime = root / ".runtime"
pidfile = runtime / "processes.json"
if args.action == "stop":
    if pidfile.exists():
        for name, item in reversed(list(json.loads(pidfile.read_text()).items())):
            try:
                # Refuse to signal a reused PID: Linux /proc start ticks identify our process.
                stat = Path(f"/proc/{item['pid']}/stat")
                actual = stat.read_text().split()[21] if stat.exists() else None
                if item["start"] is None or actual == item["start"]:
                    os.killpg(item["pid"], signal.SIGTERM)
            except (ProcessLookupError, FileNotFoundError):
                pass
        pidfile.unlink()
    print(
        "Owned native processes stopped. External Redis/Compose resources were left running."
    )
    raise SystemExit
if args.action == "status":
    print(pidfile.read_text() if pidfile.exists() else "No native process record")
    raise SystemExit
if pidfile.exists():
    raise SystemExit(
        "Process record exists. Run scripts/native.py stop before starting again."
    )
subprocess.run(["python3", "scripts/init.py"], check=True)
env = os.environ.copy()
for line in (root / ".env").read_text().splitlines():
    if line and not line.startswith("#"):
        k, v = line.split("=", 1)
        env.setdefault(k, v)
processes = {}


def start(name, command, overrides=None, health=None):
    log = open(runtime / f"{name}.log", "ab")
    e = env | (overrides or {})
    process = subprocess.Popen(
        command,
        cwd=root,
        env=e,
        stdout=log,
        stderr=subprocess.STDOUT,
        start_new_session=True,
    )
    stat = Path(f"/proc/{process.pid}/stat")
    processes[name] = {
        "pid": process.pid,
        "start": stat.read_text().split()[21] if stat.exists() else None,
    }
    pidfile.write_text(json.dumps(processes, indent=2))
    log.close()
    if health:
        for attempt in range(90):
            if process.poll() is not None:
                raise RuntimeError(f"{name} exited; inspect .runtime/{name}.log")
            try:
                with urllib.request.urlopen(health, timeout=1) as response:
                    if response.status == 200:
                        return
            except Exception:
                time.sleep(1)
        raise RuntimeError(
            f"{name} did not become healthy; inspect .runtime/{name}.log"
        )


def jar(module):
    path = root / f"code/{module}/target/{module}-1.0.0-SNAPSHOT.jar"
    if not path.exists():
        raise RuntimeError("Build first: scripts/build.sh")
    return ["java", "-Xms64m", "-Xmx256m", "-jar", str(path)]


try:
    if args.redis:
        start("redis", ["redis-server", str(runtime / "redis.conf")])
    if args.simulator:
        start(
            "simulator",
            jar("auth-server"),
            {
                "SERVER_SERVLET_SESSION_COOKIE_NAME": "SIMULATOR_SESSION",
                "AUTH_PORT": "9001",
                "AUTH_ISSUER": "http://127.0.0.1:9001",
                "AUTH_DB_URL": "jdbc:h2:file:./.runtime/simulator-db;DB_CLOSE_ON_EXIT=FALSE",
                "SIGNING_KEY_PATH": str(runtime / "simulator-signing.pem"),
                "BFF_CLIENT_ID": "upstream-client",
                "BFF_CLIENT_SECRET": env["UPSTREAM_CLIENT_SECRET"],
                "BFF_CALLBACK": "http://127.0.0.1:9000/login/oauth2/code/simulator",
                "BFF_LOGOUT": "http://127.0.0.1:9000/login",
            },
            "http://127.0.0.1:9001/actuator/health",
        )
    extra = (
        {
            "SPRING_CONFIG_ADDITIONAL_LOCATION": "optional:file:./infra/config/providers-simulator.yml"
        }
        if args.simulator
        else {}
    )
    start(
        "auth-server",
        jar("auth-server"),
        extra,
        "http://127.0.0.1:9000/actuator/health",
    )
    start(
        "flight-service",
        jar("flight-service"),
        health="http://127.0.0.1:8082/actuator/health",
    )
    start("bff", jar("bff"), health="http://127.0.0.1:8081/actuator/health")
    if not (root / "code/ui/dist/index.html").exists():
        raise RuntimeError("Build UI first: scripts/build.sh")
    start(
        "ui",
        [
            "python3",
            "-m",
            "http.server",
            "5173",
            "--bind",
            "127.0.0.1",
            "--directory",
            "code/ui/dist",
        ],
        health="http://127.0.0.1:5173/",
    )
    start("gateway", jar("gateway"), health="http://127.0.0.1:8080/actuator/health")
except Exception as e:
    print(str(e))
    subprocess.run(["python3", "scripts/native.py", "stop"])
    raise SystemExit(1)
print(
    "Ready: http://localhost:8080 . Use scripts/native.py stop to stop owned processes."
)
