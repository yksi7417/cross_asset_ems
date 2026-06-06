#!/usr/bin/env python3
"""Strip Obsidian wikilinks from Mermaid code blocks.

Mermaid.js does not understand [[wikilink]] syntax. Inside any ```mermaid
block, transform:
    [[target|alias]]  ->  alias
    [[target]]        ->  target

Leave wikilinks in prose untouched.
"""
from __future__ import annotations
import re
import sys
from pathlib import Path

VAULT = Path("/home/yksi7417/dvlp/cross_asset_ems")

MERMAID_BLOCK = re.compile(
    r"(```mermaid\n)(.*?)(\n```)",
    re.DOTALL,
)

# Wikilink patterns inside a mermaid block.
WIKILINK_WITH_ALIAS = re.compile(r"\[\[([^\[\]|]+)\|([^\[\]]+)\]\]")
WIKILINK_PLAIN      = re.compile(r"\[\[([^\[\]|]+)\]\]")

def strip_wikilinks(mermaid_body: str) -> tuple[str, int]:
    """Strip wikilinks; return new body + count of replacements."""
    count = 0
    new = WIKILINK_WITH_ALIAS.sub(lambda m: (count_inc(), m.group(2))[1], mermaid_body)
    # Recount because the closure trick above can't capture; redo straight:
    n1 = len(WIKILINK_WITH_ALIAS.findall(mermaid_body))
    new = WIKILINK_WITH_ALIAS.sub(r"\2", mermaid_body)
    n2 = len(WIKILINK_PLAIN.findall(new))
    new = WIKILINK_PLAIN.sub(r"\1", new)
    return new, n1 + n2


def count_inc():
    return 0  # dummy for the closure trick above (unused)


def process_file(path: Path) -> tuple[int, int]:
    """Return (blocks_modified, wikilinks_removed)."""
    text = path.read_text()
    blocks_modified = 0
    wikilinks_removed = 0

    def repl(match: re.Match) -> str:
        nonlocal blocks_modified, wikilinks_removed
        opener, body, closer = match.group(1), match.group(2), match.group(3)
        new_body, n = strip_wikilinks(body)
        if n > 0:
            blocks_modified += 1
            wikilinks_removed += n
        return f"{opener}{new_body}{closer}"

    new_text = MERMAID_BLOCK.sub(repl, text)
    if new_text != text:
        path.write_text(new_text)
    return blocks_modified, wikilinks_removed


def main() -> int:
    files = sorted(VAULT.rglob("*.md"))
    # Skip irrelevant trees
    skip_parts = {".git", "node_modules", "attachments"}
    files = [
        f for f in files
        if not any(p in skip_parts for p in f.parts)
    ]

    total_blocks = 0
    total_links = 0
    affected: list[tuple[Path, int, int]] = []
    for f in files:
        try:
            blocks, links = process_file(f)
        except Exception as e:
            print(f"ERROR processing {f}: {e}", file=sys.stderr)
            continue
        if links > 0:
            affected.append((f.relative_to(VAULT), blocks, links))
            total_blocks += blocks
            total_links += links

    print(f"\n{'File':70s}  Blocks  Wikilinks")
    print("-" * 95)
    for rel, b, l in affected:
        print(f"{str(rel):70s}  {b:6d}  {l:9d}")
    print("-" * 95)
    print(f"{'TOTAL':70s}  {total_blocks:6d}  {total_links:9d}")
    print(f"\nFiles affected: {len(affected)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
