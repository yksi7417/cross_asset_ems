#!/usr/bin/env python3
"""Anti-stub check — a module cannot be listed as done while it is a header stub.

Why this exists: ``cpp/`` already contains fifteen module directories, twenty-one
files and a green CI check, and no behaviour whatsoever. Every file is a
``#pragma once`` stub or a generated FSM header. This check makes that outcome
fail the build the moment a manifest entry claims otherwise.

Input is ``scripts/ci/slice-manifest.yaml``, which declares per language and per
module what the port claims:

    stub         directory exists, no behaviour. Must NOT contain non-generated
                 implementation source — if it does, the manifest is stale.
    in-progress  behaviour landing. Exempt from the behaviour checks; the
                 directory must exist.
    done         must contain compiled behaviour AND at least one test that
                 asserts something.

Usage:
    python3 scripts/ci/checks/anti_stub.py [--manifest PATH] [--root PATH]

Exit 0 when clean, 1 with one ``module: message`` line per violation.

See docs/decisions/0004-defensive-gate-stack.md and docs/polyglot/gate.md.
"""

from __future__ import annotations

import argparse
import pathlib
import sys

VALID_STATUSES = ("stub", "in-progress", "done")

IMPL_EXTENSIONS = {
    "cpp": (".cpp", ".cc", ".cxx"),
    "rust": (".rs",),
}
HEADER_EXTENSIONS = {
    "cpp": (".hpp", ".hh", ".h"),
    "rust": (),
}

# Substrings that mean a test file actually asserts something rather than merely
# existing. Deliberately covers GoogleTest, Catch2 and Rust's assert family.
ASSERTION_MARKERS = (
    "EXPECT_",
    "ASSERT_",
    "REQUIRE",
    "CHECK(",
    "CHECK_",
    "assert!",
    "assert_eq!",
    "assert_ne!",
    "assert_matches!",
    "debug_assert",
)

GENERATED_SEGMENTS = {"generated", "build", "target", "cmake-build-debug"}
TEST_SEGMENTS = {"test", "tests"}


def _segments(path: pathlib.Path, module_dir: pathlib.Path) -> tuple[str, ...]:
    return path.relative_to(module_dir).parts


def is_generated(path: pathlib.Path, module_dir: pathlib.Path) -> bool:
    return any(part in GENERATED_SEGMENTS for part in _segments(path, module_dir))


def is_test(path: pathlib.Path, module_dir: pathlib.Path) -> bool:
    parts = _segments(path, module_dir)
    if any(part in TEST_SEGMENTS for part in parts[:-1]):
        return True
    stem = path.stem
    return stem.endswith("_test") or stem.startswith("test_") or stem.endswith("Test")


def has_content(path: pathlib.Path) -> bool:
    """True when the file has at least one line that is neither blank nor a comment."""
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return False
    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith(("//", "/*", "*", "#", "--")):
            continue
        return True
    return False


def has_definition(path: pathlib.Path) -> bool:
    """True when a header carries a real definition rather than only declarations.

    A ``#pragma once`` stub has no braces outside preprocessor lines; a header
    with an inline function, a class body or a constexpr table does.
    """
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return False
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith(("//", "/*", "*", "#")):
            continue
        if "{" in line:
            return True
    return False


def has_inline_rust_tests(path: pathlib.Path) -> bool:
    """True for a Rust file carrying a `#[cfg(test)]` module that asserts."""
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return False
    if "#[cfg(test)]" not in text:
        return False
    return any(marker in text for marker in ASSERTION_MARKERS)


def _walk(module_dir: pathlib.Path) -> list[pathlib.Path]:
    return [p for p in sorted(module_dir.rglob("*")) if p.is_file()]


def module_evidence(language: str, module_dir: pathlib.Path) -> dict[str, bool]:
    """Classify what a module directory actually contains."""
    impl_ext = IMPL_EXTENSIONS.get(language, ())
    header_ext = HEADER_EXTENSIONS.get(language, ())

    has_impl = False
    has_header_definition = False
    has_asserting_test = False

    for path in _walk(module_dir):
        if is_generated(path, module_dir):
            continue
        suffix = path.suffix
        testish = is_test(path, module_dir)

        if testish and suffix in impl_ext + header_ext:
            text = path.read_text(encoding="utf-8", errors="replace")
            if any(marker in text for marker in ASSERTION_MARKERS):
                has_asserting_test = True
            continue

        if suffix in impl_ext and has_content(path):
            has_impl = True
            # Rust unit tests live in an inline `#[cfg(test)] mod tests` beside
            # the code, not in a separate file. Requiring a tests/ directory
            # would push Rust away from its own idiom to satisfy a checker.
            if language == "rust" and has_inline_rust_tests(path):
                has_asserting_test = True
        elif suffix in header_ext and has_definition(path):
            has_header_definition = True

    return {
        "impl": has_impl,
        "header_definition": has_header_definition,
        "asserting_test": has_asserting_test,
    }


def check(root: pathlib.Path, manifest: dict) -> list[str]:
    """Return a list of violation messages. Empty list means the tree is honest."""
    errors: list[str] = []
    root = pathlib.Path(root)

    for language, modules in sorted((manifest or {}).items()):
        for module, status in sorted((modules or {}).items()):
            where = f"{language}/{module}"
            if status not in VALID_STATUSES:
                errors.append(
                    f"{where}: unknown status {status!r} "
                    f"(expected one of {', '.join(VALID_STATUSES)})"
                )
                continue

            module_dir = root / language / module
            if not module_dir.is_dir():
                errors.append(f"{where}: module directory does not exist")
                continue

            evidence = module_evidence(language, module_dir)
            behaviour = evidence["impl"] or evidence["header_definition"]

            if status == "stub":
                if evidence["impl"]:
                    errors.append(
                        f"{where}: marked stub but contains implementation source — "
                        f"update scripts/ci/slice-manifest.yaml"
                    )
            elif status == "done":
                if not behaviour:
                    errors.append(
                        f"{where}: marked done but has no implementation source "
                        f"(a #pragma once header is not behaviour)"
                    )
                if not evidence["asserting_test"]:
                    errors.append(
                        f"{where}: marked done but has no test that asserts anything"
                    )

    return errors


def load_manifest(path: pathlib.Path) -> dict:
    import yaml  # imported lazily so the unit tests need no third-party deps

    with open(path, encoding="utf-8") as handle:
        return yaml.safe_load(handle) or {}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--manifest",
        default="scripts/ci/slice-manifest.yaml",
        help="path to the slice manifest (default: %(default)s)",
    )
    parser.add_argument(
        "--root", default=".", help="repository root (default: %(default)s)"
    )
    args = parser.parse_args(argv)

    manifest_path = pathlib.Path(args.manifest)
    if not manifest_path.is_file():
        print(f"anti-stub: manifest not found: {manifest_path}", file=sys.stderr)
        return 1

    errors = check(pathlib.Path(args.root), load_manifest(manifest_path))
    for error in errors:
        print(f"anti-stub: {error}", file=sys.stderr)
    if errors:
        print(
            f"anti-stub: {len(errors)} violation(s). "
            f"See docs/decisions/0004-defensive-gate-stack.md.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
