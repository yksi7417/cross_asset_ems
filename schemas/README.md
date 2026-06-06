# Schemas — the contract surface

Every cross-component interaction in the EMS is defined here. **Source of truth** for codegen into Java and Rust.

## Layout

| Folder | What | Owners |
|---|---|---|
| `sbe/` | SBE XML schemas: envelopes, messages, instrument templates, events. Codegen produces both Java and Rust bindings. | Architecture team |
| `fsm/` | FSM YAML definitions per [[arch-fix-fsm-design]]: Order, Route, MultiLeg/Package, VenueSession, SOR. Codegen produces state-machine structs + transition tests. | Architecture team |
| `fix-dictionaries/` | FIX 4.2 / 4.4 / 5.0 dictionaries (QuickFIX format) used by `ems-fix-bridge`. | Bridge maintainers |
| `reference-data/` | Static reference tables — day count conventions, business-day rules, currency codes, MIC codes, asset-class enums, tick-size regimes. | Refdata maintainers |
| `reject-codes/` | The canonical `EMS-<CAT>-<NNNN>` reject code catalog per [[arch-validator]]. Drives codegen of constants + human-readable messages. | Validator maintainers |

## Codegen pipeline

`tools/codegen/` reads from `schemas/` and writes into:

- `java/<module>/src/main/generated/` (Gradle build sources directory).
- `rust/<crate>/src/generated/` (Cargo build script output).

Generated code is **committed** so reviewers see what consumers actually see. Re-run `./scripts/dev/regen-schemas.sh` after any schema change.

## Versioning

- SBE schemas use `sinceVersion` for additive evolution (never reorder, never narrow).
- FSM YAML carries a `version:` field; bumped on any state/transition change.
- Reject codes are append-only; codes are never deleted, only marked `deprecated:`.

## See also

- [[arch-sbe-aeron-transport]] (the wire contract)
- [[arch-fix-fsm-design]] (FSM definition format)
- [[arch-validator]] (reject code namespace)
- [[arch-security-master]] (instrument template patterns)
