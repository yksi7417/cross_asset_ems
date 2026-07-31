#!/usr/bin/env python3
"""Unit tests for the deferred-work register check.

Run: python3 -m unittest discover -s scripts/ci/checks -p 'test_*.py'
"""

from __future__ import annotations

import pathlib
import tempfile
import unittest

from deferred_work import check, entries, markers

REGISTER = "docs/TODO.md"

GOOD_ENTRY = """# Follow-ups

## T-1 — Do the thing

**Why:** because it matters.

**Done when:** the thing is done.
"""


def tree(spec: dict[str, str]) -> pathlib.Path:
    root = pathlib.Path(tempfile.mkdtemp())
    for rel, body in spec.items():
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")
    return root


class TestDeferredWork(unittest.TestCase):
    def test_marker_with_a_matching_entry_passes(self):
        root = tree(
            {
                REGISTER: GOOD_ENTRY,
                "scripts/ci/thing.sh": "# DEFERRED: T-1 until the image is pinned\n",
            }
        )
        self.assertEqual(check(root, root / REGISTER), [])

    def test_marker_without_an_entry_fails(self):
        root = tree(
            {
                REGISTER: "# Follow-ups\n",
                "scripts/ci/thing.sh": "# DEFERRED: T-9\n",
            }
        )
        errors = check(root, root / REGISTER)
        self.assertTrue(any("has no entry" in e for e in errors), errors)

    def test_entry_without_a_why_fails(self):
        root = tree({REGISTER: "# F\n\n## T-1 — Thing\n\n**Done when:** done.\n"})
        errors = check(root, root / REGISTER)
        self.assertTrue(any("**Why:**" in e for e in errors), errors)

    def test_entry_without_a_done_when_fails(self):
        # An item with a reason but no completion criterion never closes: nobody
        # can tell whether it is finished.
        root = tree({REGISTER: "# F\n\n## T-1 — Thing\n\n**Why:** reasons.\n"})
        errors = check(root, root / REGISTER)
        self.assertTrue(any("**Done when:**" in e for e in errors), errors)

    def test_t_minus_one_in_prose_is_not_a_marker(self):
        # CorporateActionState.java says "LOCKED (T-1 before ex_date)", meaning
        # T minus one day. A bare `T-\d` pattern would have flagged it on the
        # first run; only an explicit DEFERRED: marker counts.
        root = tree(
            {
                REGISTER: "# Follow-ups\n",
                "java/x/CorporateActionState.java": (
                    "// ANNOUNCED -> LOCKED (T-1 before ex_date) -> APPLIED\n"
                ),
            }
        )
        self.assertEqual(check(root, root / REGISTER), [])

    def test_marker_is_found_with_its_line_number(self):
        root = tree({"docs/a.md": "line one\n<!-- DEFERRED: T-3 -->\n"})
        self.assertEqual(markers(root)["T-3"], ["docs/a.md:2"])

    def test_generated_sources_are_ignored(self):
        # Generated files are not hand-maintained, so a marker in one is an
        # artifact of the generator, not a deferral someone made.
        root = tree(
            {
                REGISTER: "# Follow-ups\n",
                "rust/ems-fsm/src/generated/order_fsm.rs": "// DEFERRED: T-9\n",
            }
        )
        self.assertEqual(check(root, root / REGISTER), [])

    def test_build_output_is_ignored(self):
        root = tree(
            {REGISTER: "# Follow-ups\n", "java/build/classes/X.java": "// DEFERRED: T-9\n"}
        )
        self.assertEqual(check(root, root / REGISTER), [])

    def test_entries_parses_multiple_items(self):
        root = tree(
            {
                REGISTER: (
                    "# F\n\n## T-1 — One\n\n**Why:** a.\n\n**Done when:** b.\n\n"
                    "## T-2 — Two\n\n**Why:** c.\n\n**Done when:** d.\n"
                )
            }
        )
        self.assertEqual(sorted(entries(root / REGISTER)), ["T-1", "T-2"])

    def test_an_entry_with_no_marker_anywhere_is_fine(self):
        # Most deferred work has no natural code location. The register is the
        # record; a marker is only needed where a comment would otherwise go
        # stale.
        root = tree({REGISTER: GOOD_ENTRY})
        self.assertEqual(check(root, root / REGISTER), [])

    def test_missing_register_with_no_markers_passes(self):
        self.assertEqual(check(tree({}), tree({}) / REGISTER), [])

    def test_multiple_markers_for_one_item_all_resolve(self):
        root = tree(
            {
                REGISTER: GOOD_ENTRY,
                "docs/a.md": "<!-- DEFERRED: T-1 -->\n",
                "scripts/b.sh": "# DEFERRED: T-1\n",
            }
        )
        self.assertEqual(check(root, root / REGISTER), [])
        self.assertEqual(len(markers(root)["T-1"]), 2)


if __name__ == "__main__":
    unittest.main()
