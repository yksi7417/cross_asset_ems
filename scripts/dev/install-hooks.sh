#!/usr/bin/env bash
#
# Install local git hooks from .githooks/ via core.hooksPath.
#
# Run once after cloning the repo:
#   ./scripts/dev/install-hooks.sh
#
# To bypass hooks for a single commit (rare; use sparingly):
#   git commit --no-verify

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

git config core.hooksPath .githooks
chmod +x .githooks/*

echo "Hooks installed via core.hooksPath = .githooks"
echo "Hooks active:"
ls -1 .githooks/ | grep -v '\.sample$'
