#!/usr/bin/env python3
"""Study-guide integrity check — the guide cannot drift from the code.

The source carries a marker at the usage site::

    // STUDY: crtp-static-dispatch

and ``70_concepts/idioms/crtp-static-dispatch.md`` carries a front-matter anchor
back to it::

    ---
    anchor: cpp/ems-fsm/include/ems_fsm/dispatch.hpp:42
    ---

This check asserts both directions:

* every ``STUDY:`` marker in source has a matching note;
* every note's anchor still resolves to a line carrying that note's marker.

And, since T-6, that each note is **complete** rather than merely present:

* the five required headings from the README template all appear;
* no section is empty, and no note contains ``TODO``/``TBD`` — a heading with
  nothing under it is a promise, not documentation;
* every note is linked from the section README, so nothing publishes into a
  directory nobody browses.

So moving marked code without updating the note fails the build, and so does
committing a skeleton. An unenforced study guide rots into a lie within weeks,
and a rotted study guide is worse than none — see
docs/decisions/0005-study-guide-with-enforced-anchors.md.

Usage:
    python3 scripts/ci/checks/study_guide.py [--root PATH]

Exit 0 when clean, 1 with one violation per line.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

IDIOMS_DIR = pathlib.Path("70_concepts/idioms")
SOURCE_TREES = ("java", "cpp", "rust")
SOURCE_EXTENSIONS = {
    ".java",
    ".cpp",
    ".cc",
    ".cxx",
    ".hpp",
    ".hh",
    ".h",
    ".rs",
}
SKIP_SEGMENTS = {"build", "target", ".git", "generated", "node_modules"}

MARKER_RE = re.compile(r"STUDY:\s*([a-z0-9][a-z0-9-]*)")
ANCHOR_RE = re.compile(r"^anchor:\s*(\S+):(\d+)\s*$")

# Notes that are section furniture rather than idiom notes.
NON_NOTE_FILES = {"README.md", "index.md", "idioms-index.md"}

# The headings every note must carry, from the template in the section README.
# "Related" is deliberately not required — a first note on a new theme has
# nothing to relate to yet, and a mandatory section invites a filler link.
REQUIRED_HEADINGS = (
    "## The idiom",
    "## Why it was needed here",
    "## What the naive version gets wrong",
    "## Where it lives",
    "## Cross-language contrast",
)

# Placeholder text that marks a section as unwritten. Word-boundary matched so
# prose about "the TODO register" does not trip it, but a bare TODO does.
PLACEHOLDER_RE = re.compile(r"\b(TODO|TBD|FIXME)\b")


def _iter_source_files(root: pathlib.Path):
    for tree in SOURCE_TREES:
        base = root / tree
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*")):
            if not path.is_file() or path.suffix not in SOURCE_EXTENSIONS:
                continue
            if any(part in SKIP_SEGMENTS for part in path.relative_to(root).parts):
                continue
            yield path


def markers(root: pathlib.Path) -> dict[str, list[str]]:
    """Map idiom slug -> list of ``relative/path.ext:line`` sites carrying it."""
    root = pathlib.Path(root)
    found: dict[str, list[str]] = {}
    for path in _iter_source_files(root):
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        if "STUDY:" not in text:
            continue
        rel = path.relative_to(root).as_posix()
        for lineno, line in enumerate(text.splitlines(), start=1):
            match = MARKER_RE.search(line)
            if match:
                found.setdefault(match.group(1), []).append(f"{rel}:{lineno}")
    return found


def notes(root: pathlib.Path) -> dict[str, dict]:
    """Map idiom slug -> {'path': note path, 'anchor': (file, line) or None}."""
    root = pathlib.Path(root)
    directory = root / IDIOMS_DIR
    result: dict[str, dict] = {}
    if not directory.is_dir():
        return result
    for path in sorted(directory.glob("*.md")):
        if path.name in NON_NOTE_FILES:
            continue
        anchor = None
        try:
            for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
                match = ANCHOR_RE.match(line.strip())
                if match:
                    anchor = (match.group(1), int(match.group(2)))
                    break
        except OSError:
            pass
        result[path.stem] = {
            "path": path.relative_to(root).as_posix(),
            "anchor": anchor,
        }
    return result


def _line_at(root: pathlib.Path, relative: str, lineno: int) -> str | None:
    path = root / relative
    if not path.is_file():
        return None
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    if not 1 <= lineno <= len(lines):
        return None
    return lines[lineno - 1]


def _completeness(root: pathlib.Path, found_notes: dict[str, dict]) -> list[str]:
    """One message per incomplete note: missing heading, empty section, placeholder."""
    errors: list[str] = []

    readme = root / IDIOMS_DIR / "README.md"
    readme_text = readme.read_text(encoding="utf-8", errors="replace") if readme.is_file() else ""

    for slug, note in sorted(found_notes.items()):
        text = (root / note["path"]).read_text(encoding="utf-8", errors="replace")

        for heading in REQUIRED_HEADINGS:
            if not re.search(rf"^{re.escape(heading)}\s*$", text, re.M):
                errors.append(f"{note['path']}: missing required section {heading!r}")

        match = PLACEHOLDER_RE.search(text)
        if match:
            errors.append(
                f"{note['path']}: contains placeholder {match.group(1)!r} — "
                f"an unwritten section is a promise, not documentation"
            )

        # An empty section: a heading whose body, up to the next heading or
        # EOF, has no non-blank line.
        for m in re.finditer(r"^(## [^\n]+)\n(.*?)(?=^## |\Z)", text, re.M | re.S):
            if not m.group(2).strip():
                errors.append(f"{note['path']}: section {m.group(1)!r} is empty")

        # Reachable from the README, so nothing publishes into a directory
        # nobody browses. A link is `(slug.md)` in the notes table.
        if f"{slug}.md" not in readme_text:
            errors.append(
                f"{note['path']}: not linked from {IDIOMS_DIR}/README.md — "
                f"add a row to the Notes table"
            )

    return errors


def check(root: pathlib.Path) -> list[str]:
    """Return a list of violation messages. Empty list means the guide is honest."""
    root = pathlib.Path(root)
    errors: list[str] = []
    found_markers = markers(root)
    found_notes = notes(root)

    for slug, sites in sorted(found_markers.items()):
        if slug not in found_notes:
            errors.append(
                f"{sites[0]}: STUDY marker {slug!r} has no note at "
                f"{IDIOMS_DIR}/{slug}.md"
            )

    errors.extend(_completeness(root, found_notes))

    for slug, note in sorted(found_notes.items()):
        anchor = note["anchor"]
        if anchor is None:
            errors.append(
                f"{note['path']}: note has no front-matter "
                f"'anchor: <path>:<line>' line"
            )
            continue
        relative, lineno = anchor
        line = _line_at(root, relative, lineno)
        if line is None:
            errors.append(
                f"{note['path']}: anchor {relative}:{lineno} does not resolve "
                f"(file missing or file is shorter than that)"
            )
            continue
        match = MARKER_RE.search(line)
        if match is None:
            errors.append(
                f"{note['path']}: anchor {relative}:{lineno} carries no STUDY "
                f"marker — the code moved, update the anchor"
            )
        elif match.group(1) != slug:
            errors.append(
                f"{note['path']}: anchor {relative}:{lineno} carries marker "
                f"{match.group(1)!r}, expected {slug!r}"
            )

    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--root", default=".", help="repository root (default: %(default)s)"
    )
    args = parser.parse_args(argv)

    errors = check(pathlib.Path(args.root))
    for error in errors:
        print(f"study-guide: {error}", file=sys.stderr)
    if errors:
        print(
            f"study-guide: {len(errors)} violation(s). "
            f"See 70_concepts/idioms/README.md.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
