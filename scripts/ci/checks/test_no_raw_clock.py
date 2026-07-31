#!/usr/bin/env python3
"""Unit tests for the raw-clock check.

Run: python3 -m unittest discover -s scripts/ci/checks -p 'test_*.py'
"""

from __future__ import annotations

import pathlib
import tempfile
import unittest

from no_raw_clock import check, offenders, read_baseline

MAIN = "java/ems-oms/src/main/java/io/crossasset/ems/oms/Thing.java"


def tree(spec: dict[str, str]) -> pathlib.Path:
    root = pathlib.Path(tempfile.mkdtemp())
    for rel, body in spec.items():
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")
    return root


class TestNoRawClock(unittest.TestCase):
    def test_direct_call_is_a_violation(self):
        root = tree({MAIN: "class Thing { long t = System.currentTimeMillis(); }\n"})
        errors = check(root, set())
        self.assertTrue(any("reads the wall clock" in e for e in errors), errors)

    def test_nano_time_is_a_violation(self):
        root = tree({MAIN: "class Thing { long t = System.nanoTime(); }\n"})
        self.assertTrue(check(root, set()))

    def test_whitespace_between_tokens_still_matches(self):
        # `System . currentTimeMillis ()` is legal Java and must not slip past.
        root = tree({MAIN: "class Thing { long t = System . currentTimeMillis (); }\n"})
        self.assertTrue(check(root, set()))

    def test_javadoc_naming_the_rule_is_not_a_violation(self):
        root = tree(
            {
                MAIN: (
                    "/**\n"
                    " * Never call System.currentTimeMillis() directly.\n"
                    " */\n"
                    "class Thing {}\n"
                )
            }
        )
        self.assertEqual(check(root, set()), [])

    def test_line_comment_is_not_a_violation(self):
        root = tree({MAIN: "// was System.currentTimeMillis()\nclass Thing {}\n"})
        self.assertEqual(check(root, set()), [])

    def test_baselined_file_is_allowed(self):
        root = tree({MAIN: "class Thing { long t = System.currentTimeMillis(); }\n"})
        self.assertEqual(check(root, {MAIN}), [])

    def test_stale_baseline_entry_is_a_violation(self):
        # A baseline that outlives the problem stops describing reality and
        # starts granting blanket permission.
        root = tree({MAIN: "class Thing {}\n"})
        errors = check(root, {MAIN})
        self.assertTrue(any("no longer reads the wall clock" in e for e in errors), errors)

    def test_system_time_source_is_permitted(self):
        root = tree(
            {
                "java/ems-core/src/main/java/io/crossasset/ems/core/clock/SystemTimeSource.java": (
                    "class SystemTimeSource { long t = System.currentTimeMillis(); }\n"
                )
            }
        )
        self.assertEqual(check(root, set()), [])

    def test_test_sources_are_ignored(self):
        # Tests are not replayed and are not part of the conformance gate.
        root = tree(
            {
                "java/ems-oms/src/test/java/io/crossasset/ems/oms/ThingTest.java": (
                    "class ThingTest { long t = System.currentTimeMillis(); }\n"
                )
            }
        )
        self.assertEqual(check(root, set()), [])

    def test_generated_sources_are_ignored(self):
        root = tree(
            {
                "java/ems-fsm/src/main/generated/OrderFsm.java": (
                    "class OrderFsm { long t = System.currentTimeMillis(); }\n"
                )
            }
        )
        self.assertEqual(check(root, set()), [])

    def test_offenders_reports_line_numbers(self):
        root = tree({MAIN: "class Thing {\n  long t = System.nanoTime();\n}\n"})
        self.assertEqual(offenders(root)[MAIN][0][0], 2)

    def test_baseline_parsing_ignores_comments_and_blanks(self):
        root = tree({})
        path = root / "baseline.txt"
        path.write_text("# note\n\n" + MAIN + "  # why\n", encoding="utf-8")
        self.assertEqual(read_baseline(path), {MAIN})

    def test_clean_tree_passes(self):
        root = tree({MAIN: "class Thing { long t = timeSource.nowMicros(); }\n"})
        self.assertEqual(check(root, set()), [])


if __name__ == "__main__":
    unittest.main()
