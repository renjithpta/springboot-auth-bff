#!/usr/bin/env python3
"""Creates private development credentials and persistent signing keys without overwriting existing ones."""
from pathlib import Path
import secrets, subprocess, os

root = Path(__file__).resolve().parents[1]
os.chdir(root)
runtime = root / ".runtime"
runtime.mkdir(exist_ok=True)
runtime.chmod(0o700)
env = root / ".env"
if not env.exists():
    values = {
        name: secrets.token_urlsafe(28)
        for name in [
            "AUTH_DB_PASSWORD",
            "BFF_CLIENT_SECRET",
            "UPSTREAM_CLIENT_SECRET",
            "REDIS_PASSWORD",
            "DEMO_PASSWORD",
        ]
    }
    values["TOKEN_ENCRYPTION_KEY"] = (
        __import__("base64").b64encode(secrets.token_bytes(32)).decode()
    )
    values["DEMO_USERS"] = "true"
    env.write_text("".join(f"{k}={v}\n" for k, v in values.items()))
    env.chmod(0o600)
for name in ["signing", "simulator-signing"]:
    path = runtime / f"{name}.pem"
    if not path.exists():
        subprocess.run(
            [
                "openssl",
                "genpkey",
                "-algorithm",
                "RSA",
                "-pkeyopt",
                "rsa_keygen_bits:3072",
                "-out",
                str(path),
            ],
            check=True,
            stderr=subprocess.DEVNULL,
        )
        path.chmod(0o600)
values = dict(
    line.split("=", 1)
    for line in env.read_text().splitlines()
    if line and not line.startswith("#")
)
(runtime / "redis.conf").write_text(
    f"bind 127.0.0.1\nport 6379\nrequirepass {values['REDIS_PASSWORD']}\ndir {runtime}\nappendonly yes\n"
)
(runtime / "redis.conf").chmod(0o600)
for role, names in {
    "redis": ["REDIS_PASSWORD"],
    "auth": ["AUTH_DB_PASSWORD", "BFF_CLIENT_SECRET", "DEMO_USERS", "DEMO_PASSWORD"],
    "bff": ["BFF_CLIENT_SECRET", "REDIS_PASSWORD", "TOKEN_ENCRYPTION_KEY"],
}.items():
    path = runtime / f"{role}.env"
    path.write_text("".join(f"{key}={values[key]}\n" for key in names))
    path.chmod(0o600)
print(
    "Development configuration ready. Read .env locally for the demo password; never commit it."
)
