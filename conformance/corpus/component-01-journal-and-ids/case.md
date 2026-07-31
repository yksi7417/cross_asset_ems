# component-01-journal-and-ids

**Covers:** the first component of the slice — the JSONL journal codec and deterministic identifier
generation — end to end through the `ems-slice` binary in all three languages.

**Why it exists:** it is the smallest case that can fail for a byte-level reason. Every later case
inherits the codec, so if this one diverges nothing after it can be trusted. It deliberately
exercises the parts of the format where three independent JSON writers are most likely to disagree:

| Input line | What it proves |
|---|---|
| 1 | `SessionLogon` establishes the session the orders reference, and its `SessionAccepted` echoes the grants at all three AND-gate layers (added when the AAA component landed; without it the orders would be rejected and this case would stop testing the codec) |
| 2, 3 | `OrderNew` → `OrderAccepted` with `ORD-0000000001`, `ORD-0000000002` — the identifier format and its ordering |
| 4 | a non-order event passes through with only its sequence renumbered |
| 5 | the `ignored` field is **not** echoed — only the agreed field list crosses to the output, so a stray field cannot diverge on some other language's map ordering |
| 6 | UTF-8 (`café — ☕`) is emitted raw, while `"` and `\` are escaped — the two rules that a "just use the JSON library" implementation gets wrong in opposite directions |
| all | output keys are lexicographic at both levels; the final line is `\n`-terminated |

The trailing `RunSummary` puts the input size and the seed in the journal itself, so a case that
silently read zero events cannot pass by producing an empty file.

**Seed:** 0 (see `seed`).

**Scope, stated plainly:** there is no validation pipeline, no FSM, no routing and no venue in this
case, because none of that is implemented yet. The `OrderNew` → `OrderAccepted` transformation
exercises the codec, the identifier generator and the entitlement check; it is not yet a claim
about the order path. When the validator and FSM land, this case's expectation changes again and
that change is reviewed like any other — as happened when AAA landed and line 1 was added.

**Reviewed against:** nothing in `schemas/` constrains this case yet — it predates the FSM and
reject-code paths. Its expectation was read line by line against the codec contract in
`conformance/README.md` and the assertions in `JournalCodecTest`, `journal_test.rs` and
`journal_test.cpp`, which all three implementations pass independently.
