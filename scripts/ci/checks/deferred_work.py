#!/usr/bin/env python3
"""Deferred work is registered, and the register does not rot.

The practice this enforces: **when work is consciously deferred, it goes in
``docs/polyglot/TODO.md`` with an ID, a reason, and a "done when" — in the same
commit that defers it.** Not in a commit message, not in a PR comment, not in
someone's head.

Why it needs a check rather than a convention. This repository already ran the
experiment: ``io.crossasset.ems.core.clock.Clock`` documented "never call
``System.currentTimeMillis()`` directly" in task 3.6, nothing enforced it, and
four services called it anyway *while their own javadoc claimed the clock was
injected*. ADR 0005 draws the same conclusion about the study guide. A rule
nobody checks is a rule the code stops following.

Two directions, mirroring ``study_guide.py``:

* every ``DEFERRED: T-n`` marker in the tree resolves to an entry in the
  register — so a comment cannot outlive the work it points at;
* every entry in the register is well formed — an ID heading, a **Why**, and a
  **Done when** — so an entry cannot be a shrug.

An explicit ``DEFERRED:`` marker rather than a bare ``T-1``: ``T-1`` already
appears in ``CorporateActionState.java`` meaning "T minus one day", which a
naive pattern would have flagged on the first run.

Usage:
    python3 scripts/ci/checks/deferred_work.py [--root PATH] [--register PATH]

Exit 0 when clean, 1 with one violation per line.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

REGISTER = pathlib.Path("docs/polyglot/TODO.md")

MARKER_RE = re.compile(r"DEFERRED:\s*(T-\d+)")
HEADING_RE = re.compile(r"^##\s+(T-\d+)\s*[—-]", re.M)

# Sections an entry must carry. "Why" without "Done when" is a complaint; "Done
# when" without "Why" is a task nobody can prioritise.
REQUIRED_SECTIONS = ("**Why:**", "**Done when:**")

SEARCH_DIRS = ("docs", "scripts", "conformance", "java", "rust", "cpp", "tools")
SEARCH_SUFFIXES = {
    ".md", ".txt", ".py", ".sh", ".java", ".rs", ".hpp", ".cpp", ".cc", ".h",
    ".yaml", ".yml", ".toml", ".kts",
}
SKIP_SEGMENTS = {"build", "target", ".git", "node_modules", "generated", "_deps"}

# This check's own tests contain marker strings as fixtures, not as deferrals.
# Scanning them makes the check fail on itself — which it did, on the first run.
# Narrow by path rather than skipping every `test_*.py`: a real deferral in a
# real test ("this case is disabled, DEFERRED: T-4") is legitimate and should be
# registered like any other.
SELF_TEST = "scripts/ci/checks/test_deferred_work.py"


def _iter_files(root: pathlib.Path):
    for name in SEARCH_DIRS:
        base = root / name
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*")):
            if not path.is_file() or path.suffix not in SEARCH_SUFFIXES:
                continue
            relative = path.relative_to(root)
            if any(part in SKIP_SEGMENTS for part in relative.parts):
                continue
            if relative.as_posix() == SELF_TEST:
                continue
            yield path


def markers(root: pathlib.Path) -> dict[str, list[str]]:
    """Map item id -> ``path:line`` sites carrying a ``DEFERRED:`` marker."""
    root = pathlib.Path(root)
    found: dict[str, list[str]] = {}
    for path in _iter_files(root):
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        if "DEFERRED:" not in text:
            continue
        rel = path.relative_to(root).as_posix()
        for lineno, line in enumerate(text.splitlines(), start=1):
            for match in MARKER_RE.finditer(line):
                found.setdefault(match.group(1), []).append(f"{rel}:{lineno}")
    return found


def entries(register: pathlib.Path) -> dict[str, str]:
    """Map item id -> the body of its section in the register."""
    register = pathlib.Path(register)
    if not register.is_file():
        return {}
    text = register.read_text(encoding="utf-8", errors="replace")

    result: dict[str, str] = {}
    matches = list(HEADING_RE.finditer(text))
    for i, match in enumerate(matches):
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        result[match.group(1)] = text[match.start() : end]
    return result


def check(root: pathlib.Path, register: pathlib.Path) -> list[str]:
    """Return violation messages. Empty means the register describes reality."""
    root = pathlib.Path(root)
    found_markers = markers(root)
    found_entries = entries(register)
    register_name = pathlib.Path(register).as_posix()
    errors: list[str] = []

    for item, sites in sorted(found_markers.items()):
        if item not in found_entries:
            errors.append(
                f"{sites[0]}: DEFERRED marker {item} has no entry in {register_name} — "
                f"either add one, or drop the marker if the work is done"
            )

    for item, body in sorted(found_entries.items()):
        for section in REQUIRED_SECTIONS:
            if section not in body:
                errors.append(
                    f"{register_name}: {item} is missing a {section} section — "
                    f"a deferred item without one cannot be prioritised or closed"
                )

    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", default=".")
    parser.add_argument("--register", default=str(REGISTER))
    args = parser.parse_args(argv)

    root = pathlib.Path(args.root)
    errors = check(root, root / args.register)
    for error in errors:
        print(f"deferred-work: {error}", file=sys.stderr)
    if errors:
        print(
            f"deferred-work: {len(errors)} violation(s). See {REGISTER}.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
