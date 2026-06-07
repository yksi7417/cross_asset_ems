#!/usr/bin/env bash
#
# gen-changelog.sh — render CHANGELOG.md from Conventional Commits using
# git-cliff and ./cliff.toml.
#
# Usage:
#   scripts/release/gen-changelog.sh              # full history -> CHANGELOG.md
#   scripts/release/gen-changelog.sh --unreleased # latest tag..HEAD -> CHANGELOG-unreleased.md
#
# Install git-cliff if missing:
#   cargo install git-cliff
#   brew install git-cliff
#   apt install git-cliff

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

if ! command -v git-cliff >/dev/null 2>&1; then
    cat >&2 <<'EOF'
ERROR: git-cliff not found. Install via:
  cargo install git-cliff   # any platform with rustup
  brew install git-cliff    # macOS
  apt install git-cliff     # Debian/Ubuntu
EOF
    exit 1
fi

case "${1:-}" in
    --unreleased)
        git-cliff --unreleased --output CHANGELOG-unreleased.md
        echo "Wrote CHANGELOG-unreleased.md"
        ;;
    --help|-h)
        cat <<EOF
Usage: $(basename "$0") [--unreleased]

  (no args)        Render full CHANGELOG.md from initial commit to HEAD
  --unreleased     Render CHANGELOG-unreleased.md from latest vX.Y.Z tag to HEAD
EOF
        exit 0
        ;;
    "")
        git-cliff --output CHANGELOG.md
        echo "Wrote CHANGELOG.md"
        ;;
    *)
        echo "Unknown arg: $1. See --help." >&2
        exit 1
        ;;
esac
