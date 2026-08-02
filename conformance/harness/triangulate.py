#!/usr/bin/env python3
"""Three-way agreement check: which implementation is the odd one out?

The per-implementation diff answers "does this output match the committed
expectation?" — a question that privileges whoever generated the expectation
(Java, per ADR 0009). A Java bug faithfully reproduced by both ports passes it.
This check asks the symmetric question instead: **do the implementations agree
with each other**, and if not, which one stands alone?

The verdicts, and why each blocks or passes:

* ``UNANIMOUS``  — every output byte-identical and equal to the expectation.
* ``2-1 SPLIT``  — one implementation disagrees with the other two. Strong
  evidence the minority is wrong: two independent implementations rarely make
  the same mistake. The report **names the minority without asserting it is
  wrong** — the two could share a bug, and deciding is a human's job. Blocks.
* ``STALE EXPECTATION`` — all implementations agree with each other but not
  with the committed expectation. Either all three changed together (suspect)
  or the expectation was not regenerated (common). Distinct verdict because the
  fix is different: regenerate or investigate, not debug a port. Blocks.
* ``NO AGREEMENT`` — every output differs from every other. Something is badly
  broken; pairwise reports are printed. Blocks.

With only two implementations present a split cannot name a minority; the
verdict degrades honestly to ``DISAGREEMENT`` between the pair. One
implementation is not triangulation at all and is reported as such.

Usage:
    python3 conformance/harness/triangulate.py --expected exp.jsonl \\
        java=out1.jsonl rust=out2.jsonl cpp=out3.jsonl

Exit 0 on UNANIMOUS, 1 on anything else.
"""

from __future__ import annotations

import argparse
import pathlib
import sys

from differ import diff


def verdict(expected: bytes, outputs: dict[str, bytes]) -> tuple[str, list[str]]:
    """The verdict name and its report lines.

    ``outputs`` maps implementation name to raw bytes, in the order given on
    the command line — which is reference-first by convention, and the order
    ties are reported in.
    """
    names = list(outputs)
    if len(names) < 2:
        return (
            "NOT TRIANGULATED",
            [f"only {len(names)} implementation(s) present — nothing to compare against"],
        )

    # Group identical outputs. Insertion order keeps reports stable.
    groups: dict[bytes, list[str]] = {}
    for name, raw in outputs.items():
        groups.setdefault(raw, []).append(name)

    if len(groups) == 1:
        agreed = next(iter(groups))
        if agreed == expected:
            return ("UNANIMOUS", [])
        report = ["all implementations agree with each other but NOT with expected.jsonl"]
        first = diff(expected, agreed)
        if first:
            report.append("difference from the committed expectation:")
            report.extend(f"  {line}" for line in first.splitlines())
        report.append(
            "either every implementation changed together, or the expectation is stale —"
        )
        report.append("regenerate it from the reference and review the diff")
        return ("STALE EXPECTATION", report)

    if len(groups) == 2 and len(names) >= 3:
        by_size = sorted(groups.values(), key=len)
        if len(by_size[0]) == 1:
            minority = by_size[0][0]
            majority = by_size[1]
            report = [
                f"{minority} stands alone; {' and '.join(majority)} agree with each other",
                "strong evidence the minority is wrong — two independent implementations "
                "rarely make the same mistake — but not proof; the majority could share a bug",
            ]
            first = diff(outputs[majority[0]], outputs[minority])
            if first:
                report.append(f"difference ({majority[0]} vs {minority}):")
                report.extend(f"  {line}" for line in first.splitlines())
            return ("2-1 SPLIT", report)

    # Ordered after the pair check below would misfire: with two
    # implementations, "every group is distinct" is just a disagreement, and
    # calling it NO AGREEMENT would imply a third opinion that never existed.
    if len(groups) == len(names) and len(names) >= 3:
        report = ["every implementation disagrees with every other"]
        for i, a in enumerate(names):
            for b in names[i + 1 :]:
                first = diff(outputs[a], outputs[b])
                if first:
                    report.append(f"{a} vs {b}:")
                    report.append(f"  {first.splitlines()[0]}")
        return ("NO AGREEMENT", report)

    # Two implementations, two groups.
    a, b = names[0], names[1]
    report = [f"{a} and {b} disagree — two implementations cannot name a minority"]
    first = diff(outputs[a], outputs[b])
    if first:
        report.extend(f"  {line}" for line in first.splitlines())
    return ("DISAGREEMENT", report)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--expected", type=pathlib.Path, required=True)
    parser.add_argument(
        "outputs",
        nargs="+",
        help="name=path pairs, reference implementation first",
    )
    args = parser.parse_args(argv)

    outputs: dict[str, bytes] = {}
    for pair in args.outputs:
        name, _, raw_path = pair.partition("=")
        path = pathlib.Path(raw_path)
        if not name or not raw_path or not path.is_file():
            print(f"triangulate: bad output argument: {pair}", file=sys.stderr)
            return 2
        outputs[name] = path.read_bytes()

    if not args.expected.is_file():
        print(f"triangulate: not a file: {args.expected}", file=sys.stderr)
        return 2

    name, report = verdict(args.expected.read_bytes(), outputs)
    if name == "UNANIMOUS":
        return 0
    print(f"triangulate: {name}", file=sys.stderr)
    for line in report:
        print(f"triangulate: {line}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
