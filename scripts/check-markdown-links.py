#!/usr/bin/env python3
"""Fail when a repository Markdown file contains a missing relative target."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import unquote


LINK = re.compile(r"(?<!!)\[[^\]]*\]\(([^)]+)\)")
REMOTE_SCHEMES = ("http://", "https://", "mailto:", "tel:")


def markdown_files(root: Path) -> list[Path]:
    files = [root / "README.md", root / "CONTRIBUTING.md"]
    files.extend((root / "docs").rglob("*.md"))
    files.extend((root / "examples").rglob("*.md"))
    return sorted(path for path in set(files) if path.is_file())


def relative_target(raw_target: str) -> str | None:
    target = raw_target.strip()
    if target.startswith("<") and target.endswith(">"):
        target = target[1:-1]
    if not target or target.startswith("#") or target.startswith(REMOTE_SCHEMES):
        return None
    target = target.split("#", 1)[0].split("?", 1)[0]
    return unquote(target) or None


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []
    checked = 0
    for document in markdown_files(root):
        text = document.read_text(encoding="utf-8")
        for line_number, line in enumerate(text.splitlines(), start=1):
            for match in LINK.finditer(line):
                target = relative_target(match.group(1))
                if target is None:
                    continue
                checked += 1
                resolved = (document.parent / target).resolve()
                if not resolved.exists():
                    failures.append(
                        f"{document.relative_to(root)}:{line_number}: missing {match.group(1)}"
                    )
    if failures:
        print("Broken relative Markdown links:", file=sys.stderr)
        print("\n".join(failures), file=sys.stderr)
        return 1
    print(f"Markdown link audit: {checked} relative targets checked")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
