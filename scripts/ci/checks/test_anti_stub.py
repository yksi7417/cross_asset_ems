#!/usr/bin/env python3
"""Unit tests for the anti-stub check.

Run: python3 -m unittest discover -s scripts/ci/checks -p 'test_*.py'
"""

from __future__ import annotations

import pathlib
import tempfile
import unittest

from anti_stub import check


def tree(spec: dict[str, str]) -> pathlib.Path:
    """Materialise a throwaway source tree from {relative path: contents}."""
    root = pathlib.Path(tempfile.mkdtemp())
    for rel, body in spec.items():
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")
    return root


class TestAntiStub(unittest.TestCase):
    def test_done_module_without_source_fails(self):
        root = tree(
            {
                "cpp/ems-oms/CMakeLists.txt": "",
                "cpp/ems-oms/include/ems_oms/ems_oms.hpp": "#pragma once\n",
            }
        )
        errors = check(root, {"cpp": {"ems-oms": "done"}})
        self.assertTrue(
            any("no implementation source" in e for e in errors), errors
        )

    def test_done_module_without_asserting_test_fails(self):
        root = tree({"cpp/ems-oms/src/order.cpp": "int f() { return 1; }\n"})
        errors = check(root, {"cpp": {"ems-oms": "done"}})
        self.assertTrue(
            any("no test that asserts" in e for e in errors), errors
        )

    def test_test_file_without_assertion_does_not_count(self):
        root = tree(
            {
                "cpp/ems-oms/src/order.cpp": "int f() { return 1; }\n",
                "cpp/ems-oms/test/order_test.cpp": "TEST(a, b) { f(); }\n",
            }
        )
        errors = check(root, {"cpp": {"ems-oms": "done"}})
        self.assertTrue(
            any("no test that asserts" in e for e in errors), errors
        )

    def test_done_module_with_source_and_assertion_passes(self):
        root = tree(
            {
                "cpp/ems-oms/src/order.cpp": "int f() { return 1; }\n",
                "cpp/ems-oms/test/order_test.cpp": "TEST(a, b) { EXPECT_EQ(f(), 1); }\n",
            }
        )
        self.assertEqual(check(root, {"cpp": {"ems-oms": "done"}}), [])

    def test_header_only_module_with_definition_passes(self):
        root = tree(
            {
                "cpp/ems-fsm/include/ems_fsm/fsm.hpp": (
                    "#pragma once\nconstexpr int next(int s) { return s + 1; }\n"
                ),
                "cpp/ems-fsm/test/fsm_test.cpp": "TEST(a, b) { EXPECT_EQ(next(1), 2); }\n",
            }
        )
        self.assertEqual(check(root, {"cpp": {"ems-fsm": "done"}}), [])

    def test_pragma_once_only_header_is_not_behaviour(self):
        root = tree(
            {
                "cpp/ems-fsm/include/ems_fsm/fsm.hpp": "#pragma once\n",
                "cpp/ems-fsm/test/fsm_test.cpp": "TEST(a, b) { EXPECT_EQ(1, 1); }\n",
            }
        )
        errors = check(root, {"cpp": {"ems-fsm": "done"}})
        self.assertTrue(
            any("no implementation source" in e for e in errors), errors
        )

    def test_generated_source_does_not_count_as_behaviour(self):
        root = tree(
            {
                "rust/ems-fsm/src/generated/order_fsm.rs": "pub fn next() -> u8 { 1 }\n",
                "rust/ems-fsm/tests/fsm_test.rs": "fn t() { assert_eq!(1, 1); }\n",
            }
        )
        errors = check(root, {"rust": {"ems-fsm": "done"}})
        self.assertTrue(
            any("no implementation source" in e for e in errors), errors
        )

    def test_stub_module_with_real_source_fails(self):
        root = tree({"cpp/ems-oms/src/order.cpp": "int f() { return 1; }\n"})
        errors = check(root, {"cpp": {"ems-oms": "stub"}})
        self.assertTrue(
            any("marked stub but contains" in e for e in errors), errors
        )

    def test_stub_module_with_only_a_header_stub_passes(self):
        root = tree(
            {
                "cpp/ems-ops/CMakeLists.txt": "",
                "cpp/ems-ops/include/ems_ops/ems_ops.hpp": "#pragma once\n",
            }
        )
        self.assertEqual(check(root, {"cpp": {"ems-ops": "stub"}}), [])

    def test_missing_module_directory_fails(self):
        errors = check(tree({}), {"rust": {"ems-oms": "done"}})
        self.assertTrue(any("does not exist" in e for e in errors), errors)

    def test_unknown_status_fails(self):
        root = tree({"cpp/ems-oms/src/order.cpp": "int f() { return 1; }\n"})
        errors = check(root, {"cpp": {"ems-oms": "nearly"}})
        self.assertTrue(any("unknown status" in e for e in errors), errors)

    def test_in_progress_module_is_exempt_but_must_exist(self):
        root = tree({"rust/ems-oms/src/lib.rs": "pub fn f() -> u8 { 1 }\n"})
        self.assertEqual(check(root, {"rust": {"ems-oms": "in-progress"}}), [])

    def test_empty_manifest_passes(self):
        self.assertEqual(check(tree({}), {}), [])


class TestInlineRustTests(unittest.TestCase):
    """Rust puts unit tests in the file they test; the check must accept that."""

    def test_inline_cfg_test_module_counts_as_a_test(self):
        root = tree(
            {
                "rust/ems-slice/src/main.rs": (
                    "fn run() -> u8 { 1 }\n"
                    "#[cfg(test)]\n"
                    "mod tests {\n"
                    "    #[test]\n"
                    "    fn t() { assert_eq!(super::run(), 1); }\n"
                    "}\n"
                )
            }
        )
        self.assertEqual(check(root, {"rust": {"ems-slice": "done"}}), [])

    def test_cfg_test_module_without_an_assertion_does_not_count(self):
        root = tree(
            {
                "rust/ems-slice/src/main.rs": (
                    "fn run() -> u8 { 1 }\n"
                    "#[cfg(test)]\n"
                    "mod tests {\n"
                    "    #[test]\n"
                    "    fn t() { super::run(); }\n"
                    "}\n"
                )
            }
        )
        errors = check(root, {"rust": {"ems-slice": "done"}})
        self.assertTrue(any("no test that asserts" in e for e in errors), errors)

    def test_source_without_any_cfg_test_still_needs_a_test_file(self):
        root = tree({"rust/ems-oms/src/lib.rs": "pub fn f() -> u8 { 1 }\n"})
        errors = check(root, {"rust": {"ems-oms": "done"}})
        self.assertTrue(any("no test that asserts" in e for e in errors), errors)


if __name__ == "__main__":
    unittest.main()
