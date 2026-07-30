# Devcontainer image for the Cross-Asset EMS.
#
# The toolchain is NOT installed with RUN apt-get lines here. It is installed by
# scripts/ci/install-toolchain.sh, which every CI job also runs — one definition
# of the gate's dependency set, so the image and CI cannot drift apart.
#
# See docs/polyglot/gate.md and docs/decisions/0004-defensive-gate-stack.md.

FROM mcr.microsoft.com/devcontainers/java:21-noble

# The `full` profile covers everything `scripts/ci/gate.sh full` needs, which is
# what a developer wants locally. `nightly` adds Valgrind, Miri and cargo-fuzz;
# it is deliberately not baked in — those are long-running tools that belong in
# the scheduled lane, not in every developer's image.
COPY scripts/ci/install-toolchain.sh /tmp/install-toolchain.sh
RUN /tmp/install-toolchain.sh --profile full \
    && rm -f /tmp/install-toolchain.sh \
    && rm -rf /var/lib/apt/lists/*

USER vscode
