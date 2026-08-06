#!/usr/bin/env python3
"""Validate repository documentation links, lifecycle markers and active-state hygiene."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
ARCHIVE_ROOT = ROOT / "docs" / "archive"

MARKDOWN_ROOTS = (
    ROOT / "README.md",
    ROOT / "BRANCHING.md",
    ROOT / "AGENTS.md",
)

VOLATILE_ACTIVE_MARKERS = (
    "Active implementation branch:",
    "Active pull request:",
    "local integration candidate",
    "review PR pending",
    "origin/dev` a `2850d03",
    "Current remote integration baseline:\n\n```text\n2850d03",
)

REQUIRED_ACTIVE_SOURCES = (
    ROOT / "docs" / "README.md",
    ROOT / "docs" / "current-state.md",
    ROOT / "docs" / "roadmap.md",
    ROOT / "docs" / "implementation-plan.md",
    ROOT / "docs" / "definition-of-done.md",
    ROOT / "docs" / "releases" / "harness-0.5.md",
)

LINK_PATTERN = re.compile(r"!?(?:\[[^\]]*\])\(([^)]+)\)")


def markdown_files() -> list[Path]:
    files = [path for path in MARKDOWN_ROOTS if path.exists()]
    files.extend(sorted((ROOT / "docs").rglob("*.md")))
    files.extend(sorted(ROOT.glob("*/AGENTS.md")))
    files.extend(sorted(ROOT.glob("*/*/AGENTS.md")))
    return sorted(set(files))


def content_without_fenced_code(text: str) -> str:
    output: list[str] = []
    in_fence = False
    for line in text.splitlines():
        if line.lstrip().startswith("```"):
            in_fence = not in_fence
            continue
        if not in_fence:
            output.append(line)
    return "\n".join(output)


def local_link_target(source: Path, raw_target: str) -> Path | None:
    target = raw_target.strip()
    if target.startswith("<") and target.endswith(">"):
        target = target[1:-1]
    target = target.split(maxsplit=1)[0]
    parsed = urlsplit(target)
    if parsed.scheme or parsed.netloc or target.startswith("#"):
        return None
    path_text = unquote(parsed.path)
    if not path_text:
        return None
    if path_text.startswith("/"):
        return ROOT / path_text.lstrip("/")
    return (source.parent / path_text).resolve()


def validate_links(path: Path, text: str) -> list[str]:
    errors: list[str] = []
    for match in LINK_PATTERN.finditer(content_without_fenced_code(text)):
        raw_target = match.group(1)
        target = local_link_target(path, raw_target)
        if target is None:
            continue
        try:
            target.relative_to(ROOT)
        except ValueError:
            errors.append(f"{path.relative_to(ROOT)}: link escapes repository: {raw_target}")
            continue
        if not target.exists():
            errors.append(f"{path.relative_to(ROOT)}: missing local link target: {raw_target}")
    return errors


def validate_archive(path: Path, text: str) -> list[str]:
    if ARCHIVE_ROOT not in path.parents:
        return []
    if "Status: historical" not in text:
        return [f"{path.relative_to(ROOT)}: archived document must contain 'Status: historical'"]
    return []


def validate_active_state(path: Path, text: str) -> list[str]:
    if ARCHIVE_ROOT in path.parents:
        return []
    errors: list[str] = []
    for marker in VOLATILE_ACTIVE_MARKERS:
        if marker.casefold() in text.casefold():
            errors.append(f"{path.relative_to(ROOT)}: obsolete active-state marker: {marker!r}")
    if path.name == "AGENTS.md" and "docs/archive/" in text:
        errors.append(f"{path.relative_to(ROOT)}: coding-agent guides must not route normal work to docs/archive")
    return errors


def main() -> int:
    errors: list[str] = []
    for required in REQUIRED_ACTIVE_SOURCES:
        if not required.exists():
            errors.append(f"missing required active source: {required.relative_to(ROOT)}")

    for path in markdown_files():
        text = path.read_text(encoding="utf-8")
        errors.extend(validate_links(path, text))
        errors.extend(validate_archive(path, text))
        errors.extend(validate_active_state(path, text))

    if errors:
        print("Documentation validation failed:", file=sys.stderr)
        for error in sorted(set(errors)):
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"Documentation validation passed for {len(markdown_files())} Markdown files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
