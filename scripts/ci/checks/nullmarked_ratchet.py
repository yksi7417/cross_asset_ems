#!/usr/bin/env python3
"""@NullMarked ratchet — null-correctness coverage can grow but never shrink.

NullAway runs in ``OnlyNullMarked`` mode (see
``build-logic/src/main/kotlin/ems.java-conventions.gradle.kts``): it checks
exactly the packages that declare themselves null-correct with JSpecify's
``@NullMarked`` and ignores the rest. Turning the whole 684-file tree on at once
produces hundreds of errors in code that never claimed to be null-correct, which
is an outage rather than a gate.

The obvious failure mode of an opt-in check is that someone deletes the opt-in
to make a build pass. This ratchet closes it:

* every package listed in ``scripts/ci/nullmarked-baseline.txt`` must still
  carry ``@NullMarked``;
* every package that carries ``@NullMarked`` must be listed in the baseline.

So coverage only moves one way, and moving it is a visible, reviewable edit to a
committed file.

Usage:
    python3 scripts/ci/checks/nullmarked_ratchet.py [--baseline PATH] [--root PATH]

Exit 0 when clean, 1 with one violation per line.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)


def marked_packages(root: pathlib.Path) -> set[str]:
    """Packages whose package-info.java carries @NullMarked."""
    root = pathlib.Path(root)
    found: set[str] = set()
    java_root = root / "java"
    if not java_root.is_dir():
        return found
    for path in sorted(java_root.rglob("package-info.java")):
        if "build" in path.relative_to(root).parts:
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        if "@NullMarked" not in text:
            continue
        match = PACKAGE_RE.search(text)
        if match:
            found.add(match.group(1))
    return found


def read_baseline(path: pathlib.Path) -> set[str]:
    entries: set[str] = set()
    if not pathlib.Path(path).is_file():
        return entries
    for raw in pathlib.Path(path).read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if line:
            entries.add(line)
    return entries


def check(root: pathlib.Path, baseline: set[str]) -> list[str]:
    actual = marked_packages(root)
    errors: list[str] = []

    for package in sorted(baseline - actual):
        errors.append(
            f"{package}: listed in the baseline but no longer @NullMarked — "
            f"restore the annotation instead of removing the baseline entry"
        )
    for package in sorted(actual - baseline):
        errors.append(
            f"{package}: is @NullMarked but missing from the baseline — "
            f"add it to scripts/ci/nullmarked-baseline.txt"
        )
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--baseline",
        default="scripts/ci/nullmarked-baseline.txt",
        help="path to the baseline list (default: %(default)s)",
    )
    parser.add_argument(
        "--root", default=".", help="repository root (default: %(default)s)"
    )
    args = parser.parse_args(argv)

    errors = check(pathlib.Path(args.root), read_baseline(pathlib.Path(args.baseline)))
    for error in errors:
        print(f"nullmarked: {error}", file=sys.stderr)
    if errors:
        print(f"nullmarked: {len(errors)} violation(s).", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
