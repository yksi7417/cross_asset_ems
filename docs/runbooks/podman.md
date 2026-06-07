# Podman on Fedora / RHEL — Dev Stack Runbook

This runbook documents every Podman-specific issue encountered running the
`ems-dev` stack on Fedora with rootless Podman, and the fix applied in each
case. Cross-referenced from [SETUP.md — Troubleshooting](../../SETUP.md).

---

## Why Podman behaves differently from Docker

| Behaviour | Docker | Rootless Podman (Fedora) |
|---|---|---|
| Container runtime | Daemon (`dockerd`, runs as root) | Daemonless, runs in user namespace |
| Port forwarding | `dockerd` binds host ports directly | `rootlessport` / `pasta` bridges user → host |
| Registry resolution | Unqualified names → Docker Hub | Unqualified names → `registry.fedoraproject.org` first |
| Bind-mount file access | Host files readable by container | SELinux label must be `container_file_t` |
| `docker compose` command | Built-in plugin (`docker-compose-plugin`) | Routes to `podman-compose` (limited v2 compatibility) |

---

## Installation

```bash
sudo dnf install podman podman-docker podman-compose
systemctl --user enable --now podman.socket   # Docker-socket shim for tools that need /var/run/docker.sock
```

`podman-docker` installs a `docker` binary that proxies to `podman`, so all
`docker compose ...` commands in the project scripts work unchanged.

---

## Issue: SELinux blocks bind-mounted config files

**Symptom**

```
open /etc/otelcol/config.yaml: permission denied
```

Containers crash on startup even though the host file has `644` permissions.
Affected services: `otel-collector`, `prometheus`, `grafana`, `postgres-init`.

**Root cause**

SELinux enforces that container processes may only read files labelled
`container_file_t`. Files checked out from git have the owner's home label
(`user_home_t`). The container process is denied regardless of Unix
permissions.

**Fix — `security_opt: label:disable` per service**

The standard workaround is to disable SELinux label enforcement per service.
This is safe for a local dev stack where you own and trust the bind-mounted files:

```yaml
services:
  otel-collector:
    security_opt: [label:disable]   # SELinux: allow bind-mount reads on Fedora/Podman
    volumes:
      - ../otel-collector/config.yaml:/etc/otelcol/config.yaml:ro,z
```

Already applied to `postgres`, `prometheus`, `grafana`, and `otel-collector`
in `infra/docker-compose/compose.dev.yaml`. Docker ignores `label:disable` on
non-SELinux systems, so this is safe to leave in for cross-platform use.

**Why `:z` alone is not enough with `podman-compose`**

The `:z` volume option tells Podman to run `chcon -t container_file_t` on the
host path at mount time. However, some versions of `podman-compose` silently
drop compound volume options (e.g., `:ro,z`), so the relabeling never happens.
`security_opt: label:disable` bypasses this at the container level and works
regardless of the `podman-compose` version.

**`:z` vs `:Z` (for reference)**

| Option | Meaning | Use when |
|---|---|---|
| `:z` | Shared label — multiple containers can read | Config files shared across containers |
| `:Z` | Private label — only this container can read | Secrets or exclusive data dirs |

**Permanent per-directory relabeling (alternative, requires root)**

```bash
sudo dnf install -y policycoreutils-python-utils   # provides semanage
sudo semanage fcontext -a -t container_file_t "$(pwd)/infra(/.*)?"
sudo restorecon -Rv infra/
```

This adds a permanent SELinux policy rule so `restorecon` and `git checkout`
preserve the `container_file_t` label automatically.

**Verify SELinux is enforcing** (explains why this only affects Fedora/RHEL):

```bash
getenforce    # Enforcing → SELinux active; Permissive → logging only; Disabled → off
```

---

## Issue: Images pulled from wrong registry

**Symptom**

```
Error: unable to copy from source docker://registry.fedoraproject.org/postgres:17-alpine: manifest unknown
```

**Root cause**

Podman searches `registry.fedoraproject.org` before Docker Hub for unqualified
image names (`postgres:17-alpine` instead of `docker.io/library/postgres:17-alpine`).

**Fix — fully qualify every image name**

```yaml
# Before
image: postgres:17-alpine
image: prom/prometheus:v3.0.0

# After
image: docker.io/library/postgres:17-alpine
image: docker.io/prom/prometheus:v3.0.0
```

Rule: single-component names (`postgres`) need `docker.io/library/` prefix.
Two-component names (`prom/prometheus`) need `docker.io/` prefix.
Already-qualified images (`ghcr.io/…`) are unchanged.

Already applied to all images in `infra/docker-compose/compose.dev.yaml`.

---

## Issue: Port 9090 conflict with Cockpit

**Symptom**

```
rootlessport listen tcp 0.0.0.0:9090: bind: address already in use
```

**Root cause**

Fedora ships Cockpit (web-based system management UI) bound to port 9090 by default.

**Fix — remap Prometheus host port**

```yaml
# Before
ports: ["9090:9090"]

# After — container still listens on 9090 internally
ports: ["9091:9090"]   # 9090 conflicts with Cockpit on Fedora/RHEL
```

Grafana reaches Prometheus via `http://prometheus:9090` (container network),
so no datasource config change is needed. Prometheus UI is at `http://localhost:9091`.

Already applied in `infra/docker-compose/compose.dev.yaml`.

To stop Cockpit for the session instead: `sudo systemctl stop cockpit.socket`.

---

## Issue: `docker compose ps <service>` fails

**Symptom**

```
podman-compose: error: unrecognized arguments: postgres
```

**Root cause**

`podman-compose` (the Python reimplementation) has incomplete Docker Compose v2
compatibility. It does not support `docker compose ps <service> --format json`.

**Fix — use direct TCP/HTTP health probes**

`scripts/dev/start-dev-stack.sh` was rewritten to check service readiness by
probing the actual service ports rather than parsing `docker compose ps` output:

```bash
# Postgres — bash TCP probe
bash -c "exec 3<>/dev/tcp/localhost/5432" 2>/dev/null

# OpenSearch — HTTP health endpoint
curl -fs http://localhost:9200/_cluster/health
```

This works with `podman-compose`, Docker Compose v2, or any other backend.

---

## Issue: Tailscale machines can't reach the dev stack

**Symptom**

Services are up locally but unreachable from other machines on the Tailscale network.

**Root cause**

Fedora's `firewalld` does not automatically trust the `tailscale0` interface.
Incoming connections from Tailscale peers are dropped at the firewall.

**Fix**

```bash
sudo firewall-cmd --zone=trusted --add-interface=tailscale0 --permanent
sudo firewall-cmd --reload

# Verify
sudo firewall-cmd --zone=trusted --list-interfaces   # should show: tailscale0

# Find your Tailscale IP
tailscale ip -4   # e.g. 100.64.x.x
```

After this, all dev stack services are reachable at your Tailscale IP from
any machine on your tailnet.

---

## Issue: "Emulate Docker CLI using podman" noise

**Symptom**

Every `docker` command prints:

```
Emulate Docker CLI using podman. Create /etc/containers/nodocker to quiet msg.
>>>> Executing external compose provider "/usr/bin/podman-compose". ...
```

**Fix**

```bash
sudo touch /etc/containers/nodocker
```

The first line disappears. The `podman-compose` provider message persists — it
comes from Podman's compose dispatch, not from `podman-docker`.

---

## Useful diagnostics

```bash
# What containers are running and what ports are mapped?
podman ps --format "{{.Names}} {{.Ports}}"

# Is a port actually bound at the OS level? (podman ps can lie for rootlessport failures)
ss -tlnp | grep 4317

# Container logs
docker compose -f infra/docker-compose/compose.dev.yaml logs otel-collector --tail 40
docker compose -f infra/docker-compose/compose.dev.yaml logs prometheus --tail 20
docker compose -f infra/docker-compose/compose.dev.yaml logs postgres --tail 20

# SELinux audit log (see what was denied)
sudo ausearch -m avc -ts recent | tail -20

# Is SELinux enforcing?
getenforce

# What SELinux label does a file have?
ls -Z infra/otel-collector/config.yaml
```
