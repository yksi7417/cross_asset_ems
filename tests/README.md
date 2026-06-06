# tests/

Cross-module test code — distinct from per-module unit tests (those live in each module's own `src/test/`).

| Folder | Purpose |
|---|---|
| `integration/` | Component-level integration tests using in-process Aeron + SBE mocks. ~500 tests per [[arch-ddd-tdd]] test pyramid. Run on every PR. |
| `smoke/` | Smoke tests for environments — verify a deployed instance handles golden-path orders for one asset class. Run after deploy. |
| `e2e/` | End-to-end BDD-style scenarios. ~50 scenarios per [[arch-ddd-tdd]]. Full Docker Compose stack. Exercises every Appendix D race condition per [[arch-fix-appendix-d]]. Run nightly + on release-candidate. |

Per-module unit tests stay in `java/<module>/src/test/` and `rust/<crate>/tests/`. The pyramid goal is ~5,000 unit + 500 component + 50 BDD; see [[arch-ddd-tdd]].
