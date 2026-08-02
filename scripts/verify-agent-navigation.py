#!/usr/bin/env python3
"""Validate coding-agent navigation and repository module discoverability."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
ROOT_GUIDE = ROOT / "AGENTS.md"
SETTINGS = ROOT / "settings.gradle.kts"

IGNORED_PARTS = {
    ".git",
    ".gradle",
    ".idea",
    "build",
    "third_party",
}

REQUIRED_HEADINGS = {
    "## Start here",
    "## Non-negotiable architecture invariants",
    "## Repository map",
    "## Validation commands",
    "## Maintaining `AGENTS.md`",
}

REQUIRED_LINK_TARGETS = {
    "README.md",
    "docs/architecture.md",
    "docs/roadmap.md",
    "docs/implementation-plan.md",
    "docs/definition-of-done.md",
    "docs/adr/README.md",
}

MARKDOWN_LINK = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
GRADLE_MODULE = re.compile(r'"(:[^"\n]+)"')


def discover_guides() -> list[Path]:
    guides: list[Path] = []
    for path in ROOT.rglob("AGENTS.md"):
        relative = path.relative_to(ROOT)
        if any(part in IGNORED_PARTS for part in relative.parts):
            continue
        guides.append(path)
    return sorted(guides)


def local_link_target(raw_target: str) -> str | None:
    target = raw_target.strip().split(maxsplit=1)[0].strip("<>")
    if not target or target.startswith(("#", "http://", "https://", "mailto:")):
        return None
    return unquote(target.split("#", maxsplit=1)[0])


def validate_links(guide: Path, errors: list[str]) -> None:
    text = guide.read_text(encoding="utf-8")
    for raw_target in MARKDOWN_LINK.findall(text):
        target = local_link_target(raw_target)
        if target is None:
            continue
        resolved = (guide.parent / target).resolve()
        try:
            resolved.relative_to(ROOT)
        except ValueError:
            errors.append(
                f"{guide.relative_to(ROOT)}: link escapes repository: {raw_target}"
            )
            continue
        if not resolved.exists():
            errors.append(
                f"{guide.relative_to(ROOT)}: missing link target: {raw_target}"
            )


def validate_root_guide(errors: list[str]) -> None:
    if not ROOT_GUIDE.is_file():
        errors.append("missing root AGENTS.md")
        return

    text = ROOT_GUIDE.read_text(encoding="utf-8")

    for heading in sorted(REQUIRED_HEADINGS):
        if heading not in text:
            errors.append(f"AGENTS.md: missing required heading: {heading}")

    for target in sorted(REQUIRED_LINK_TARGETS):
        if f"]({target})" not in text:
            errors.append(f"AGENTS.md: missing canonical document link: {target}")

    if not SETTINGS.is_file():
        errors.append("missing settings.gradle.kts")
        return

    settings_text = SETTINGS.read_text(encoding="utf-8")
    modules = sorted(set(GRADLE_MODULE.findall(settings_text)))
    if not modules:
        errors.append("settings.gradle.kts: no Gradle modules discovered")
        return

    for module in modules:
        module_path = module.lstrip(":").replace(":", "/")
        if f"`{module_path}`" not in text:
            errors.append(
                "AGENTS.md: Gradle module is not present in repository map: "
                f"{module} -> {module_path}"
            )


def validate_filename_casing(errors: list[str]) -> None:
    for path in ROOT.rglob("*.md"):
        relative = path.relative_to(ROOT)
        if any(part in IGNORED_PARTS for part in relative.parts):
            continue
        if path.name.lower() == "agents.md" and path.name != "AGENTS.md":
            errors.append(
                f"use exact AGENTS.md casing instead of {relative.as_posix()}"
            )
        if path.name.lower() == "agent.md":
            errors.append(
                f"remove competing guide {relative.as_posix()}; use AGENTS.md"
            )


def main() -> int:
    errors: list[str] = []

    validate_root_guide(errors)
    validate_filename_casing(errors)

    guides = discover_guides()
    if ROOT_GUIDE.is_file() and ROOT_GUIDE not in guides:
        guides.insert(0, ROOT_GUIDE)

    for guide in guides:
        validate_links(guide, errors)

    if errors:
        print("Agent navigation validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        "Agent navigation is valid: "
        f"{len(guides)} guide(s), all configured modules discoverable."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
