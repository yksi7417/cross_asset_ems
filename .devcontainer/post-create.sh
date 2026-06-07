#!/usr/bin/env bash
# Runs once after the dev container is created.
# Java 21 is already provided by the base image.
set -eo pipefail

echo "==> Installing C++ build tools..."
sudo apt-get update -qq
sudo apt-get install -y -qq cmake ninja-build g++-14

echo "==> Installing Python dev tools..."
pip3 install --user pytest pyyaml jsonschema

echo "==> Generating Gradle wrapper..."
./scripts/dev/bootstrap.sh

echo
echo "Dev container ready. Start the dev infra stack on your host:"
echo "  ./scripts/dev/start-dev-stack.sh   (run on the host, not inside the container)"
echo "Then use ./gradlew and cmake inside this container as normal."
