# scripts/

Operational scripts. All bash unless explicitly noted.

| Folder | Purpose |
|---|---|
| `dev/` | Developer-local scripts — `regen-schemas.sh`, `start-dev-stack.sh`, `run-trader-demo.sh` (one-command trader-desktop demo, see `docs/TRADER_DESKTOP_DEMO.md`), `tail-logs.sh`. |
| `ci/` | CI-only scripts called by GitHub Actions — `run-unit-tests.sh`, `run-component-tests.sh`, `sbom-scan.sh`, `cosign-sign.sh`. |
| `release/` | Release machinery — `tag-release.sh`, `promote-image.sh`. |
| `drills/` | Resilience drills per [[arch-resilience-24x7]] — `weekly-leader-kill.sh`, `monthly-cold-start.sh`, `quarterly-cross-region-failover.sh`. |

## Conventions

- `#!/usr/bin/env bash` + `set -euo pipefail` at the top of every script.
- Errors exit non-zero with a descriptive message to stderr.
- Use shellcheck — `shellcheck scripts/**/*.sh` runs in CI.
- Pass arguments explicitly, never via env vars (except for CI/CD secrets).
