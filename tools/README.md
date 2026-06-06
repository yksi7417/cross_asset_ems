# tools/

Build-time tooling — distinct from the runtime services in `java/` and `rust/`.

| Folder | Purpose |
|---|---|
| `codegen/` | Schema-to-source codegen. Reads from `schemas/`, writes into `java/<module>/src/main/generated/` and `rust/<crate>/src/generated/`. Driven by `scripts/dev/regen-schemas.sh`. |
| `replay/` | Replay CLI for the event log. Takes a log slice + target code version + clock source, produces a re-derived event stream + diff vs the original. Used in CI's "golden replay" verification step. |
| `fsm-validator/` | Validates FSM YAML definitions for completeness (every state has at least one transition out, every event is handled by at least one state, no unreachable states). Runs in CI. |

These tools are written in Java or Rust depending on what's most natural — they all build into standalone jars / binaries that the CI and dev scripts call.
