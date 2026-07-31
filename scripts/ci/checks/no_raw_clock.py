#!/usr/bin/env python3
"""Business logic must take a TimeSource, not read the wall clock directly.

``io.crossasset.ems.core.clock.Clock`` has said so in its javadoc since task
3.6. Nothing checked it, and four services — ``InMemoryAaaService``,
``InMemoryStagedOrderManager``, ``InMemoryRouteManager`` and
``InMemoryMultiLegOrderManager`` — called ``System.currentTimeMillis()`` anyway
while their own javadoc claimed the clock was injected. A documented rule that
nothing enforces is a rule the code stops following.

Why it matters beyond tidiness: a component that reads the wall clock cannot be
replayed, cannot be tested against a fixed instant, and cannot participate in
the polyglot conformance gate, which compares three implementations byte-for-byte
and would see a different timestamp every run.

Not every read is wrong. An I/O deadline (`while (now < deadline)` around an
Aeron offer) is genuinely wall-clock work and has nothing to do with business
time. Those files are listed in ``scripts/ci/clock-baseline.txt`` and the list
can shrink but not grow.

Usage:
    python3 scripts/ci/checks/no_raw_clock.py [--baseline PATH] [--root PATH]

Exit 0 when clean, 1 with one violation per line.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

# The one class permitted to read the system clock: everything else takes a
# TimeSource, which is what makes the rule enforceable rather than aspirational.
PERMITTED = {"java/ems-core/src/main/java/io/crossasset/ems/core/clock/SystemTimeSource.java"}

CLOCK_CALL_RE = re.compile(r"System\s*\.\s*(currentTimeMillis|nanoTime)\s*\(")


def is_comment(line: str) -> bool:
    """True for a line that is only a comment — javadoc naming the rule is not a breach of it."""
    stripped = line.strip()
    return stripped.startswith(("*", "//", "/*"))


def offenders(root: pathlib.Path) -> dict[str, list[tuple[int, str]]]:
    """Map relative file path -> [(line number, source line)] for every raw clock read."""
    root = pathlib.Path(root)
    found: dict[str, list[tuple[int, str]]] = {}
    java_root = root / "java"
    if not java_root.is_dir():
        return found

    for path in sorted(java_root.rglob("*.java")):
        relative = path.relative_to(root).as_posix()
        parts = path.relative_to(root).parts
        # Main source only. Tests may read the wall clock; they are not replayed.
        if "src" not in parts or "main" not in parts or "build" in parts:
            continue
        if "generated" in parts or relative in PERMITTED:
            continue

        text = path.read_text(encoding="utf-8", errors="replace")
        # Fast path on "System" alone, not "System.": `System . nanoTime ()` is
        # legal Java, and a check that a reformat can evade is not a check.
        if "System" not in text:
            continue
        for lineno, line in enumerate(text.splitlines(), start=1):
            if is_comment(line):
                continue
            if CLOCK_CALL_RE.search(line):
                found.setdefault(relative, []).append((lineno, line.strip()))
    return found


def read_baseline(path: pathlib.Path) -> set[str]:
    entries: set[str] = set()
    path = pathlib.Path(path)
    if not path.is_file():
        return entries
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if line:
            entries.add(line)
    return entries


def check(root: pathlib.Path, baseline: set[str]) -> list[str]:
    """Return violation messages. Empty means business logic is clock-injectable."""
    found = offenders(root)
    errors: list[str] = []

    for relative, hits in sorted(found.items()):
        if relative in baseline:
            continue
        for lineno, line in hits:
            errors.append(
                f"{relative}:{lineno}: reads the wall clock directly — "
                f"take a TimeSource instead: {line}"
            )

    # A baseline entry that no longer offends must come out, or the list stops
    # describing reality and starts granting blanket permission.
    for stale in sorted(baseline - set(found)):
        errors.append(
            f"{stale}: listed in the clock baseline but no longer reads the wall "
            f"clock — remove it from scripts/ci/clock-baseline.txt"
        )

    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--baseline", default="scripts/ci/clock-baseline.txt")
    parser.add_argument("--root", default=".")
    args = parser.parse_args(argv)

    errors = check(pathlib.Path(args.root), read_baseline(pathlib.Path(args.baseline)))
    for error in errors:
        print(f"no-raw-clock: {error}", file=sys.stderr)
    if errors:
        print(f"no-raw-clock: {len(errors)} violation(s).", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
