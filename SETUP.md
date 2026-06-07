# Setup Guide

Full setup instructions for all platforms. For the fast path on Fedora/Podman, see [`README.md`](README.md).

---

## Prerequisites

| Tool | Min version | Why | Notes |
|---|---|---|---|
| **Docker** or **Podman** | Docker 24+ / Podman 4.4+ | Dev infrastructure stack | See platform sections below |
| **Java** (OpenJDK / Temurin) | 21 LTS | Primary language; matches `gradle.properties` | Handled by `bootstrap.sh` |
| **Gradle** | 8.10 | Java build | Wrapper committed — `./gradlew` works on clone |
| **CMake** | 3.25+ | C++ build | `dnf install cmake` / `apt install cmake` / `brew install cmake` |
| **GCC** | 14+ (or Clang 17+) | C++20 compiler | `dnf install gcc-c++` / `apt install g++-14` |
| **Python** | 3.10+ | FSM validator + lifecycle chaining tests | Usually pre-installed |
| **pytest + pyyaml + jsonschema** | latest | FSM test suite | `pip3 install --user pytest pyyaml jsonschema` |
| **git** | 2.30+ | Hooks use `git diff --check` features | System package manager |

---

## Platform: Fedora / RHEL (Podman)

> For a full record of Podman-specific issues and fixes see **[docs/runbooks/podman.md](docs/runbooks/podman.md)**.

Podman is the native container runtime on Fedora and RHEL. Use `podman-docker` for a transparent `docker` shim:

```bash
sudo dnf install podman podman-docker podman-compose git python3 python3-pip
sudo dnf install cmake ninja-build gcc-c++            # C++ build tools
pip3 install --user pytest pyyaml jsonschema
systemctl --user enable --now podman.socket           # Docker-socket compatibility shim
```

**OpenSearch — kernel memory setting**

OpenSearch requires a higher `vm.max_map_count` or it will refuse to start:

```bash
sudo sysctl -w vm.max_map_count=262144                # current session
echo "vm.max_map_count=262144" | sudo tee /etc/sysctl.d/99-opensearch.conf   # persist across reboots
```

**Tailscale — expose the dev stack to your tailnet**

By default, firewalld blocks incoming connections from the Tailscale interface. One command fixes it:

```bash
sudo firewall-cmd --zone=trusted --add-interface=tailscale0 --permanent
sudo firewall-cmd --reload
```

After that, replace `localhost` with your Tailscale IP (`tailscale ip -4`) to reach any service from other machines on your tailnet.

**Java version**

`bootstrap.sh` installs Java 21 for the current shell session. To make it the permanent system default:

```bash
sudo dnf install java-21-openjdk-devel    # if not already installed by bootstrap
sudo alternatives --set java /usr/lib/jvm/java-21-openjdk/bin/java
sudo alternatives --set javac /usr/lib/jvm/java-21-openjdk/bin/javac
```

---

## Platform: Ubuntu / Debian

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-plugin git python3 python3-pip
sudo apt-get install -y cmake ninja-build g++-14
pip3 install --user pytest pyyaml jsonschema
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"    # log out and back in after this
```

**OpenSearch — kernel memory setting** (same as Fedora):

```bash
sudo sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" | sudo tee /etc/sysctl.d/99-opensearch.conf
```

**Java 21:**

```bash
sudo apt-get install -y openjdk-21-jdk
# Or Eclipse Temurin (recommended):
# https://adoptium.net/installation/linux/
```

---

## Platform: macOS

```bash
brew install --cask docker           # Docker Desktop (includes Compose plugin)
brew install --cask temurin@21       # Java 21 Temurin
brew install cmake ninja             # C++ build tools
brew install python3
pip3 install pytest pyyaml jsonschema
```

Start Docker Desktop from the Applications folder before running the stack.

No `vm.max_map_count` change needed — Docker Desktop handles the Linux VM settings automatically.

---

## First-time repo setup

After installing prerequisites:

```bash
git clone git@github.com:yksi7417/cross_asset_ems.git
cd cross_asset_ems

# Bootstrap Java 21 (the Gradle wrapper is already committed)
./scripts/dev/bootstrap.sh

# Wire up git hooks (Conventional Commits + secret guard)
./scripts/dev/install-hooks.sh

# Pre-fetch Docker images (~2 GB, one-time)
docker compose -f infra/docker-compose/compose.dev.yaml pull
```

`bootstrap.sh` detects your distro and installs Java 21 via `dnf`/`apt`/`brew`. The Gradle wrapper (`gradlew` + `gradle/wrapper/gradle-wrapper.jar`) is committed, so `./gradlew` works immediately after clone — no wrapper generation needed.

---

## Dev container (skip native Java/Gradle entirely)

If you'd rather not install Java 21 or Gradle on your host, the project ships a Dev Container (`.devcontainer/`) that bundles the full toolchain — Java 21, Gradle 8.10, CMake 3.25, GCC 14, Python 3.10 — inside a single container image.

**VS Code:** open the repo → Command Palette → **Dev Containers: Reopen in Container**. VS Code builds the image, runs `post-create.sh` once, and opens the workspace fully configured inside the container.

**CLI:**

```bash
npm install -g @devcontainers/cli     # one-time
devcontainer up --workspace-folder .
devcontainer exec --workspace-folder . ./gradlew assemble
```

**Podman:**

```bash
export DOCKER_HOST=unix://${XDG_RUNTIME_DIR}/podman/podman.sock
devcontainer up --workspace-folder .
```

The dev infrastructure stack (Postgres, OpenSearch, etc.) still runs via Podman/Docker Compose **on the host** — start it with `./scripts/dev/start-dev-stack.sh` outside the container. The container connects to it over `localhost`.

---

## Obsidian vault (optional, recommended for design navigation)

The repository is a valid Obsidian vault. Wikilinks and graph view work out of the box:

```bash
# Fedora
flatpak install --user -y flathub md.obsidian.Obsidian
flatpak run md.obsidian.Obsidian
```

Then **Open folder as vault** → point at this repository root. See [`00_index/USAGE.md`](00_index/USAGE.md) for navigation hotkeys, the graph view, and authoring conventions.

---

## Troubleshooting

> **Podman on Fedora/RHEL** — for a full deep-dive on every Podman-specific issue (SELinux bind mounts, registry prefixes, Cockpit port conflict, `podman-compose` limitations, Tailscale firewall) see **[docs/runbooks/podman.md](docs/runbooks/podman.md)**.

| Problem | Fix |
|---|---|
| `Unable to access jarfile gradle-wrapper.jar` | The committed wrapper jar is missing from your tree. Restore with `git checkout -- gradle/wrapper/gradle-wrapper.jar`. |
| `java.lang.IllegalArgumentException: 25.0.1` during Gradle build | You have Java 25 active. Run `bootstrap.sh`; it installs and activates Java 21 for the session. |
| `SDKMAN_CANDIDATES_API: unbound variable` | `bootstrap.sh` no longer uses SDKMAN. Pull the latest and re-run. |
| OpenSearch refuses to start | `sudo sysctl -w vm.max_map_count=262144` — see OpenSearch section above. |
| `address already in use: 9090` | Cockpit (Fedora) uses port 9090. The compose file remaps Prometheus to 9091. If you still see this, run `sudo systemctl stop cockpit.socket`. |
| `permission denied` reading bind-mounted config files (otel-collector, prometheus) | SELinux blocks container access to host files without `container_file_t` label. All bind mounts in `compose.dev.yaml` use `:ro,z` — the `,z` tells Podman to relabel. Pull latest and recreate containers. |
| `registry.fedoraproject.org: manifest unknown` | Podman prefixed unqualified image names. All images in `compose.dev.yaml` are now fully qualified with `docker.io/`. Pull latest. |
| `podman-compose: unrecognized arguments: postgres` | `podman-compose` has limited Docker Compose v2 compatibility. `start-dev-stack.sh` uses portable TCP/HTTP health checks — no `docker compose ps` needed. |
| Tailscale: can't reach services from other machines | `sudo firewall-cmd --zone=trusted --add-interface=tailscale0 --permanent && sudo firewall-cmd --reload` |
| OTel toy doesn't appear in Jaeger | Verify the collector is running: `docker compose -f infra/docker-compose/compose.dev.yaml logs otel-collector`. Confirm `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317`. |
| Pre-commit rejects commit message | Must match Conventional Commits: `feat(<scope>): <subject>`. See `.githooks/commit-msg` for the full regex. |
| Pre-commit caught a secret in test fixtures | Add the path to the exclude list in `.githooks/pre-commit` (currently `.githooks/`, `tests/*/fixtures/`, `docs/decisions/`). |
