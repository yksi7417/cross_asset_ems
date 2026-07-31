#!/usr/bin/env python3
"""Byte-exact journal differ.

The comparison is on raw bytes, deliberately. A differ that normalises line
endings, trailing whitespace or key order would hide exactly the class of
cross-language divergence this gate exists to catch — and it would do so
silently, which is worse than not having the gate.

Usage:
    python3 conformance/harness/differ.py <expected> <actual>

Exit 0 when the files are byte-identical, 1 with a report otherwise.
"""

from __future__ import annotations

import argparse
import pathlib
import sys

CONTEXT_LINES = 2


def _describe(raw: bytes) -> str:
    """Render a line so invisible differences are visible."""
    return repr(raw.decode("utf-8", errors="replace"))


def diff(expected: bytes, actual: bytes) -> str | None:
    """Return a human-readable report, or None when the bytes are identical."""
    if expected == actual:
        return None

    expected_lines = expected.split(b"\n")
    actual_lines = actual.split(b"\n")

    report: list[str] = []
    for index in range(min(len(expected_lines), len(actual_lines))):
        if expected_lines[index] == actual_lines[index]:
            continue
        line_no = index + 1
        report.append(f"first difference at line {line_no}:")
        start = max(0, index - CONTEXT_LINES)
        for context in range(start, index):
            report.append(f"  {context + 1:>5}   {_describe(expected_lines[context])}")
        report.append(f"  {line_no:>5} - {_describe(expected_lines[index])}")
        report.append(f"  {line_no:>5} + {_describe(actual_lines[index])}")
        break

    if len(expected_lines) != len(actual_lines):
        difference = len(actual_lines) - len(expected_lines)
        if difference > 0:
            report.append(f"actual has {difference} extra line(s)")
            for offset, extra in enumerate(actual_lines[len(expected_lines) :]):
                report.append(f"  {len(expected_lines) + offset + 1:>5} + {_describe(extra)}")
        else:
            report.append(f"actual is missing {-difference} line(s)")
            for offset, missing in enumerate(expected_lines[len(actual_lines) :]):
                report.append(f"  {len(actual_lines) + offset + 1:>5} - {_describe(missing)}")

    if not report:
        # Same line count, every line equal, yet the bytes differ: the only way
        # that happens is a trailing-newline difference the split hid.
        report.append(
            f"byte-level difference with no line-level difference "
            f"(expected {len(expected)} bytes, actual {len(actual)})"
        )

    return "\n".join(report)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("expected", type=pathlib.Path)
    parser.add_argument("actual", type=pathlib.Path)
    args = parser.parse_args(argv)

    for path in (args.expected, args.actual):
        if not path.is_file():
            print(f"differ: not a file: {path}", file=sys.stderr)
            return 1

    report = diff(args.expected.read_bytes(), args.actual.read_bytes())
    if report is None:
        return 0
    print(report, file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
