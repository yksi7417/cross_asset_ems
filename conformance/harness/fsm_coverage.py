#!/usr/bin/env python3
"""Every FSM transition in the schemas is reached by at least one corpus case.

The conformance gate proves three implementations agree on the cases it has. It
says nothing about the cases it does not have — and a state machine is exactly
the shape where the untested transition is the one that breaks, because each is
a separate branch that no amount of exercising its neighbours covers.

Coverage is measured from **output**, not input. ``SliceRunner`` emits an
``FsmTransition`` event for every transition it takes, so a case that looks like
it should reach a transition but does not is caught. Inferring coverage from
input events would credit a case for an event the machine ignored.

Only transitions the slice can actually drive are required. ``schemas/fsm/``
defines five machines; the slice runs the order machine, so the others are
listed as out of scope rather than silently skipped — see ``--fsm``.

Usage:
    python3 conformance/harness/fsm_coverage.py [--root PATH] [--fsm NAME]

Exit 0 when every in-scope transition is covered, 1 with the uncovered ones
listed.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys

CORPUS = pathlib.Path("conformance/corpus")
SCHEMA_DIR = pathlib.Path("schemas/fsm")

# Machines the slice drives. The others are real and generated in all three
# languages, but nothing in the slice sends them events yet, so requiring corpus
# coverage would mean writing cases for behaviour that does not exist.
IN_SCOPE = {
    "order": "order.fsm.yaml",
    "route": "route.fsm.yaml",
    "venue_session": "venue_session.fsm.yaml",
}

OUT_OF_SCOPE_REASON = {
    "sor": "SOR is out of the slice entirely (ADR 0002)",
    "multileg": "multi-leg is out of the slice entirely (ADR 0002)",
}


def transitions(schema_path: pathlib.Path) -> set[tuple[str, str, str]]:
    """Every ``(from, event, to)`` the schema defines."""
    import yaml

    with open(schema_path, encoding="utf-8") as handle:
        schema = yaml.safe_load(handle)
    return {(t["from"], t["event"], t["to"]) for t in schema.get("transitions", [])}


def covered(corpus: pathlib.Path, fsm: str) -> set[tuple[str, str, str]]:
    """Every ``(from, event, to)`` an ``expected.jsonl`` shows actually happening.

    Only transitions marked ``applied=true`` count. A recorded no-transition
    proves the machine *declined* the event, which is the opposite of coverage.
    """
    found: set[tuple[str, str, str]] = set()
    corpus = pathlib.Path(corpus)
    if not corpus.is_dir():
        return found

    for expected in sorted(corpus.glob("*/expected.jsonl")):
        for line in expected.read_text(encoding="utf-8").splitlines():
            if '"FsmTransition"' not in line:
                continue
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue
            fields = event.get("fields", {})
            if fields.get("fsm") != fsm or fields.get("applied") != "true":
                continue
            found.add((fields.get("from", ""), fields.get("event", ""), fields.get("to", "")))
    return found


def check(root: pathlib.Path, only: str | None = None) -> list[str]:
    """Return one message per uncovered transition. Empty means full coverage."""
    root = pathlib.Path(root)
    errors: list[str] = []

    for fsm, filename in sorted(IN_SCOPE.items()):
        if only and fsm != only:
            continue
        schema = root / SCHEMA_DIR / filename
        if not schema.is_file():
            errors.append(f"{fsm}: schema not found at {SCHEMA_DIR / filename}")
            continue

        defined = transitions(schema)
        reached = covered(root / CORPUS, fsm)

        for source, event, target in sorted(defined - reached):
            errors.append(
                f"{fsm}: {source} -{event}-> {target} is not reached by any corpus case"
            )

    return errors


def summary(root: pathlib.Path) -> str:
    """Human-readable coverage, printed whether or not the check passes."""
    root = pathlib.Path(root)
    lines = []
    for fsm, filename in sorted(IN_SCOPE.items()):
        defined = transitions(root / SCHEMA_DIR / filename)
        reached = covered(root / CORPUS, fsm) & defined
        pct = (100 * len(reached) / len(defined)) if defined else 100.0
        lines.append(f"  {fsm}: {len(reached)}/{len(defined)} transitions ({pct:.0f}%)")
    for fsm, reason in sorted(OUT_OF_SCOPE_REASON.items()):
        lines.append(f"  {fsm}: not required — {reason}")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", default=".")
    parser.add_argument("--fsm", default=None, help="check only this machine")
    args = parser.parse_args(argv)

    root = pathlib.Path(args.root)
    print("fsm-coverage:")
    print(summary(root))

    errors = check(root, args.fsm)
    for error in errors:
        print(f"fsm-coverage: {error}", file=sys.stderr)
    if errors:
        print(
            f"fsm-coverage: {len(errors)} transition(s) not covered. "
            f"Add a corpus case that reaches them, or explain in the case.md why not.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
