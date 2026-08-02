#!/usr/bin/env python3
"""Tests for the three-way agreement check.

The one that matters most is the 2-1 split: T-3's done-when requires a
deliberate one-language divergence to be reported as a split naming the
minority, not as "rust != java". The stale-expectation verdict is the other
half of the point — it is the case the privileged-reference diff can never
see, because every implementation passes it in unison.

Run: python3 -m unittest discover -s conformance/harness -p 'test_*.py'
"""

from __future__ import annotations

import unittest

from triangulate import verdict

GOOD = b'{"seq":1}\n{"seq":2}\n'
BAD = b'{"seq":1}\n{"seq":999}\n'
WORSE = b'{"seq":777}\n'


class TestVerdicts(unittest.TestCase):
    def test_unanimous_when_all_match_the_expectation(self):
        name, report = verdict(GOOD, {"java": GOOD, "rust": GOOD, "cpp": GOOD})
        self.assertEqual(name, "UNANIMOUS")
        self.assertEqual(report, [])

    def test_a_single_divergent_implementation_is_a_named_2_1_split(self):
        name, report = verdict(GOOD, {"java": GOOD, "rust": BAD, "cpp": GOOD})
        self.assertEqual(name, "2-1 SPLIT")
        text = "\n".join(report)
        # The minority is named, and named as standing alone — not as
        # "rust != java", which would just re-privilege the reference.
        self.assertIn("rust stands alone", text)
        self.assertIn("java and cpp agree", text)
        # Named without being convicted.
        self.assertIn("not proof", text)

    def test_the_minority_can_be_the_reference(self):
        # Triangulation exists precisely so Java gets no special treatment.
        name, report = verdict(GOOD, {"java": BAD, "rust": GOOD, "cpp": GOOD})
        self.assertEqual(name, "2-1 SPLIT")
        self.assertIn("java stands alone", "\n".join(report))

    def test_three_way_agreement_against_the_expectation_is_stale(self):
        # The verdict the reference-diff can never produce: everyone agrees,
        # and everyone is different from the committed file.
        name, report = verdict(GOOD, {"java": BAD, "rust": BAD, "cpp": BAD})
        self.assertEqual(name, "STALE EXPECTATION")
        self.assertIn("agree with each other but NOT", "\n".join(report))

    def test_total_disagreement_is_its_own_verdict(self):
        name, report = verdict(GOOD, {"java": GOOD, "rust": BAD, "cpp": WORSE})
        self.assertEqual(name, "NO AGREEMENT")
        text = "\n".join(report)
        self.assertIn("java vs rust", text)
        self.assertIn("rust vs cpp", text)

    def test_two_implementations_cannot_name_a_minority(self):
        name, report = verdict(GOOD, {"java": GOOD, "rust": BAD})
        self.assertEqual(name, "DISAGREEMENT")
        self.assertIn("cannot name a minority", "\n".join(report))

    def test_two_implementations_agreeing_with_expectation_is_unanimous(self):
        name, _ = verdict(GOOD, {"java": GOOD, "rust": GOOD})
        self.assertEqual(name, "UNANIMOUS")

    def test_one_implementation_is_not_triangulation(self):
        # Degrading to a silent pass here would let a one-binary CI run claim
        # three-way agreement it never checked.
        name, _ = verdict(GOOD, {"java": GOOD})
        self.assertEqual(name, "NOT TRIANGULATED")

    def test_the_split_report_shows_the_first_difference(self):
        _, report = verdict(GOOD, {"java": GOOD, "rust": BAD, "cpp": GOOD})
        self.assertIn("first difference at line 2", "\n".join(report))


if __name__ == "__main__":
    unittest.main()
