#!/usr/bin/env python3
"""Unit tests for the byte-exact differ.

Run: python3 -m unittest discover -s conformance/harness -p 'test_*.py'
"""

from __future__ import annotations

import unittest

from differ import diff


class TestDiffer(unittest.TestCase):
    def test_identical_returns_none(self):
        self.assertIsNone(diff(b"a\nb\n", b"a\nb\n"))

    def test_empty_files_are_identical(self):
        self.assertIsNone(diff(b"", b""))

    def test_reports_first_differing_line_number(self):
        report = diff(b"a\nb\nc\n", b"a\nX\nc\n")
        self.assertIn("first difference at line 2", report)

    def test_reports_extra_lines(self):
        report = diff(b"a\n", b"a\nb\n")
        self.assertIn("extra line", report)

    def test_reports_missing_lines(self):
        report = diff(b"a\nb\n", b"a\n")
        self.assertIn("missing", report)

    def test_trailing_newline_difference_is_a_difference(self):
        # A differ that let this pass would hide a real divergence: one
        # implementation terminating its last line and another not.
        self.assertIsNotNone(diff(b"a\n", b"a"))

    def test_crlf_is_a_difference(self):
        # Normalising line endings here would defeat the whole gate.
        self.assertIsNotNone(diff(b"a\n", b"a\r\n"))

    def test_trailing_whitespace_is_a_difference(self):
        self.assertIsNotNone(diff(b"a\n", b"a \n"))

    def test_key_order_difference_is_a_difference(self):
        self.assertIsNotNone(
            diff(b'{"a":"1","b":"2"}\n', b'{"b":"2","a":"1"}\n')
        )

    def test_report_shows_invisible_characters(self):
        report = diff(b"a\n", b"a\t\n")
        self.assertIn("\\t", report)

    def test_invalid_utf8_does_not_crash_the_report(self):
        report = diff(b"a\n", b"\xff\xfe\n")
        self.assertIsNotNone(report)


if __name__ == "__main__":
    unittest.main()
