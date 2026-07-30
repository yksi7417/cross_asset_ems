#!/usr/bin/env python3
"""Unit tests for the study-guide integrity check.

Run: python3 -m unittest discover -s scripts/ci/checks -p 'test_*.py'
"""

from __future__ import annotations

import pathlib
import tempfile
import unittest

from study_guide import check, markers, notes

NOTE = "---\nanchor: {anchor}\n---\n\n# {title}\n"


def tree(spec: dict[str, str]) -> pathlib.Path:
    root = pathlib.Path(tempfile.mkdtemp())
    for rel, body in spec.items():
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")
    return root


class TestStudyGuide(unittest.TestCase):
    def test_empty_tree_passes(self):
        self.assertEqual(check(tree({})), [])

    def test_marker_without_note_fails(self):
        root = tree({"rust/ems-oms/src/lib.rs": "// STUDY: typestate-fsm\nfn a() {}\n"})
        errors = check(root)
        self.assertTrue(any("has no note" in e for e in errors), errors)

    def test_note_with_dangling_anchor_fails(self):
        root = tree(
            {
                "rust/ems-oms/src/lib.rs": "fn a() {}\n",
                "70_concepts/idioms/typestate-fsm.md": NOTE.format(
                    anchor="rust/ems-oms/src/lib.rs:1", title="Typestate FSM"
                ),
            }
        )
        errors = check(root)
        self.assertTrue(any("carries no STUDY marker" in e for e in errors), errors)

    def test_note_anchor_past_end_of_file_fails(self):
        root = tree(
            {
                "rust/ems-oms/src/lib.rs": "// STUDY: typestate-fsm\n",
                "70_concepts/idioms/typestate-fsm.md": NOTE.format(
                    anchor="rust/ems-oms/src/lib.rs:99", title="Typestate FSM"
                ),
            }
        )
        errors = check(root)
        self.assertTrue(any("does not resolve" in e for e in errors), errors)

    def test_note_without_anchor_fails(self):
        root = tree({"70_concepts/idioms/typestate-fsm.md": "# Typestate FSM\n"})
        errors = check(root)
        self.assertTrue(any("has no front-matter" in e for e in errors), errors)

    def test_anchor_pointing_at_a_different_marker_fails(self):
        root = tree(
            {
                "cpp/ems-fsm/src/d.cpp": "// STUDY: crtp-static-dispatch\n",
                "70_concepts/idioms/crtp-static-dispatch.md": NOTE.format(
                    anchor="cpp/ems-fsm/src/d.cpp:1", title="CRTP"
                ),
                "70_concepts/idioms/std-expected.md": NOTE.format(
                    anchor="cpp/ems-fsm/src/d.cpp:1", title="expected"
                ),
            }
        )
        errors = check(root)
        self.assertTrue(any("expected 'std-expected'" in e for e in errors), errors)

    def test_matching_marker_and_note_passes(self):
        root = tree(
            {
                "rust/ems-oms/src/lib.rs": "// STUDY: typestate-fsm\nfn a() {}\n",
                "70_concepts/idioms/typestate-fsm.md": NOTE.format(
                    anchor="rust/ems-oms/src/lib.rs:1", title="Typestate FSM"
                ),
            }
        )
        self.assertEqual(check(root), [])

    def test_readme_is_not_treated_as_a_note(self):
        root = tree({"70_concepts/idioms/README.md": "# How to write an idiom note\n"})
        self.assertEqual(check(root), [])

    def test_markers_in_generated_sources_are_ignored(self):
        root = tree(
            {"java/ems-fsm/src/main/generated/OrderFsm.java": "// STUDY: nope\n"}
        )
        self.assertEqual(markers(root), {})
        self.assertEqual(check(root), [])

    def test_markers_reports_every_site(self):
        root = tree(
            {
                "cpp/ems-oms/src/a.cpp": "// STUDY: span-boundaries\n",
                "cpp/ems-oms/src/b.cpp": "int x;\n// STUDY: span-boundaries\n",
                "70_concepts/idioms/span-boundaries.md": NOTE.format(
                    anchor="cpp/ems-oms/src/a.cpp:1", title="span"
                ),
            }
        )
        self.assertEqual(
            markers(root)["span-boundaries"],
            ["cpp/ems-oms/src/a.cpp:1", "cpp/ems-oms/src/b.cpp:2"],
        )
        self.assertEqual(check(root), [])

    def test_notes_parses_anchor(self):
        root = tree(
            {
                "cpp/ems-oms/src/a.cpp": "// STUDY: span-boundaries\n",
                "70_concepts/idioms/span-boundaries.md": NOTE.format(
                    anchor="cpp/ems-oms/src/a.cpp:1", title="span"
                ),
            }
        )
        self.assertEqual(
            notes(root)["span-boundaries"]["anchor"], ("cpp/ems-oms/src/a.cpp", 1)
        )


if __name__ == "__main__":
    unittest.main()
