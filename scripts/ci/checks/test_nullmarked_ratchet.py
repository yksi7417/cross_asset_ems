#!/usr/bin/env python3
"""Unit tests for the @NullMarked ratchet.

Run: python3 -m unittest discover -s scripts/ci/checks -p 'test_*.py'
"""

from __future__ import annotations

import pathlib
import tempfile
import unittest

from nullmarked_ratchet import check, marked_packages, read_baseline

MARKED = "@NullMarked\npackage {pkg};\n\nimport org.jspecify.annotations.NullMarked;\n"
UNMARKED = "package {pkg};\n"


def tree(spec: dict[str, str]) -> pathlib.Path:
    root = pathlib.Path(tempfile.mkdtemp())
    for rel, body in spec.items():
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")
    return root


class TestNullMarkedRatchet(unittest.TestCase):
    def test_finds_marked_packages(self):
        root = tree(
            {
                "java/ems-validator/src/main/java/io/x/validator/package-info.java": MARKED.format(
                    pkg="io.x.validator"
                ),
                "java/ems-oms/src/main/java/io/x/oms/package-info.java": UNMARKED.format(
                    pkg="io.x.oms"
                ),
            }
        )
        self.assertEqual(marked_packages(root), {"io.x.validator"})

    def test_removing_the_annotation_fails(self):
        root = tree(
            {
                "java/ems-validator/src/main/java/io/x/validator/package-info.java": UNMARKED.format(
                    pkg="io.x.validator"
                )
            }
        )
        errors = check(root, {"io.x.validator"})
        self.assertTrue(any("no longer @NullMarked" in e for e in errors), errors)

    def test_marking_without_baseline_entry_fails(self):
        root = tree(
            {
                "java/ems-oms/src/main/java/io/x/oms/package-info.java": MARKED.format(
                    pkg="io.x.oms"
                )
            }
        )
        errors = check(root, set())
        self.assertTrue(any("missing from the baseline" in e for e in errors), errors)

    def test_matching_baseline_passes(self):
        root = tree(
            {
                "java/ems-oms/src/main/java/io/x/oms/package-info.java": MARKED.format(
                    pkg="io.x.oms"
                )
            }
        )
        self.assertEqual(check(root, {"io.x.oms"}), [])

    def test_baseline_parsing_ignores_comments_and_blanks(self):
        root = tree({})
        path = root / "baseline.txt"
        path.write_text("# a comment\n\nio.x.oms  # trailing\n", encoding="utf-8")
        self.assertEqual(read_baseline(path), {"io.x.oms"})

    def test_missing_baseline_file_is_empty(self):
        self.assertEqual(read_baseline(pathlib.Path("/nonexistent/baseline.txt")), set())

    def test_build_output_is_ignored(self):
        root = tree(
            {
                "java/ems-oms/build/generated/io/x/oms/package-info.java": MARKED.format(
                    pkg="io.x.oms"
                )
            }
        )
        self.assertEqual(marked_packages(root), set())


if __name__ == "__main__":
    unittest.main()
