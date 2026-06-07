#!/usr/bin/env python3
"""FSM YAML validator — checks structural conformance against schemas/fsm/fsm.schema.yaml
plus semantic rules not expressible in JSON Schema.

Usage:
    python3 validate.py [file.fsm.yaml ...]      # validate named files
    python3 validate.py --all                    # validate all schemas/fsm/*.fsm.yaml
    python3 validate.py --test                   # run self-tests (good + bad fixtures)
"""

import argparse
import sys
from pathlib import Path

import yaml
import jsonschema

REPO_ROOT = Path(__file__).parent.parent.parent
SCHEMA_PATH = REPO_ROOT / "schemas" / "fsm" / "fsm.schema.yaml"
FSM_DIR = REPO_ROOT / "schemas" / "fsm"


def load_schema() -> dict:
    with open(SCHEMA_PATH) as f:
        return yaml.safe_load(f)


def validate_file(path: Path, schema: dict) -> list[str]:
    """Return list of error strings; empty means valid."""
    errors: list[str] = []

    with open(path) as f:
        doc = yaml.safe_load(f)

    if not isinstance(doc, dict):
        return [f"{path}: top-level document is not a mapping"]

    # JSON Schema structural check
    validator = jsonschema.Draft202012Validator(schema)
    for err in validator.iter_errors(doc):
        errors.append(f"{path}: {err.json_path}: {err.message}")

    if errors:
        return errors  # semantic checks need a structurally valid doc

    # Semantic checks
    state_names = {s["name"] for s in doc["states"]}
    event_names = {e["name"] for e in doc["events"]}

    # Exactly one initial state
    initials = [s["name"] for s in doc["states"] if s.get("initial")]
    if len(initials) != 1:
        errors.append(
            f"{path}: expected exactly 1 initial state, found {len(initials)}: {initials}"
        )

    # Transition references valid state + event names
    used_events: set[str] = set()
    terminal_states = {s["name"] for s in doc["states"] if s.get("terminal")}
    transitions_from: dict[str, list[str]] = {s: [] for s in state_names}

    for t in doc["transitions"]:
        if t["from"] not in state_names:
            errors.append(f"{path}: transition references unknown from-state {t['from']!r}")
        if t["to"] not in state_names:
            errors.append(f"{path}: transition references unknown to-state {t['to']!r}")
        if t["event"] not in event_names:
            errors.append(f"{path}: transition references unknown event {t['event']!r}")
        used_events.add(t["event"])
        if t["from"] in state_names:
            transitions_from[t["from"]].append(t["to"])

    # Terminal states must have no outgoing transitions
    for t in doc["transitions"]:
        if t["from"] in terminal_states:
            errors.append(
                f"{path}: terminal state {t['from']!r} has outgoing transition on event {t['event']!r}"
            )

    # Every state is reachable from the initial state (BFS)
    if initials:
        reachable = {initials[0]}
        queue = [initials[0]]
        while queue:
            current = queue.pop()
            for nxt in transitions_from.get(current, []):
                if nxt not in reachable:
                    reachable.add(nxt)
                    queue.append(nxt)
        unreachable = state_names - reachable
        if unreachable:
            errors.append(f"{path}: unreachable states: {sorted(unreachable)}")

    # Every declared event must be used by at least one transition
    unused_events = event_names - used_events
    if unused_events:
        errors.append(f"{path}: declared events not used in any transition: {sorted(unused_events)}")

    return errors


def run_self_tests(schema: dict) -> bool:
    """Validate the toy good-fixture and a set of deliberately broken ones."""
    good = {
        "name": "ToyFsm",
        "version": 1,
        "kind": "order",
        "context_schema": {"order_id": {"type": "uuid"}},
        "states": [
            {"name": "INIT", "initial": True},
            {"name": "DONE", "terminal": True},
        ],
        "events": [{"name": "Start"}],
        "transitions": [{"from": "INIT", "event": "Start", "to": "DONE"}],
    }

    bad_fixtures = [
        # Missing required field
        ({k: v for k, v in good.items() if k != "name"}, "missing 'name'"),
        # Unknown kind
        ({**good, "kind": "bogus"}, "bad kind"),
        # lowercase state name
        ({**good, "states": [{"name": "init", "initial": True}, {"name": "DONE", "terminal": True}]}, "lowercase state"),
        # lowercase event name
        ({**good, "events": [{"name": "start"}]}, "lowercase event"),
    ]

    ok = True

    # Good fixture must pass
    tmp_path = Path("/tmp/_toy_good.fsm.yaml")
    tmp_path.write_text(yaml.dump(good))
    errs = validate_file(tmp_path, schema)
    if errs:
        print(f"FAIL self-test good fixture: {errs}")
        ok = False
    else:
        print("PASS self-test: good fixture validates cleanly")

    for fixture, label in bad_fixtures:
        tmp_path = Path(f"/tmp/_toy_bad_{label.replace(' ', '_')}.fsm.yaml")
        tmp_path.write_text(yaml.dump(fixture))
        errs = validate_file(tmp_path, schema)
        if errs:
            print(f"PASS self-test: bad fixture ({label}) correctly rejected")
        else:
            print(f"FAIL self-test: bad fixture ({label}) should have been rejected")
            ok = False

    return ok


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate FSM YAML files against fsm.schema.yaml")
    parser.add_argument("files", nargs="*", help="FSM YAML files to validate")
    parser.add_argument("--all", action="store_true", help="Validate all schemas/fsm/*.fsm.yaml")
    parser.add_argument("--test", action="store_true", help="Run self-tests")
    args = parser.parse_args()

    schema = load_schema()

    if args.test:
        return 0 if run_self_tests(schema) else 1

    targets: list[Path] = []
    if args.all:
        targets = sorted(FSM_DIR.glob("*.fsm.yaml"))
    targets += [Path(f) for f in args.files]

    if not targets:
        parser.print_help()
        return 0

    all_errors: list[str] = []
    for path in targets:
        errs = validate_file(path, schema)
        if errs:
            for e in errs:
                print(e, file=sys.stderr)
            all_errors.extend(errs)
        else:
            print(f"OK  {path}")

    return 1 if all_errors else 0


if __name__ == "__main__":
    sys.exit(main())
