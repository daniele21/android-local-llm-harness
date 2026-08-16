#!/usr/bin/env python3
"""Read the canonical Gradle module inventory from settings.gradle.kts."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from typing import Sequence

REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SETTINGS = REPOSITORY_ROOT / "settings.gradle.kts"
_INCLUDE_BLOCK = re.compile(r"\binclude\s*\((.*?)\)", re.DOTALL)
_MODULE_LITERAL = re.compile(r'["\'](:[^"\']+)["\']')


def load_gradle_modules(settings_path: Path = DEFAULT_SETTINGS) -> tuple[str, ...]:
    """Return Gradle project paths without their leading colon, preserving settings order."""
    text = settings_path.read_text(encoding="utf-8")
    modules: list[str] = []
    for block in _INCLUDE_BLOCK.findall(text):
        modules.extend(match.group(1).removeprefix(":") for match in _MODULE_LITERAL.finditer(block))

    if not modules:
        raise ValueError(f"No Gradle modules found in {settings_path}")
    if len(modules) != len(set(modules)):
        duplicates = sorted(module for module in set(modules) if modules.count(module) > 1)
        raise ValueError(f"Duplicate Gradle modules in {settings_path}: {', '.join(duplicates)}")
    return tuple(modules)


def validate_module_roots(modules: Sequence[str], repository_root: Path = REPOSITORY_ROOT) -> None:
    """Fail when settings names a module that has no Gradle build file."""
    missing = []
    for module in modules:
        module_dir = repository_root.joinpath(*module.split(":"))
        if not ((module_dir / "build.gradle.kts").is_file() or (module_dir / "build.gradle").is_file()):
            missing.append(module)
    if missing:
        raise ValueError(f"Gradle modules without build files: {', '.join(missing)}")


def main() -> int:
    try:
        modules = load_gradle_modules()
        validate_module_roots(modules)
    except (OSError, ValueError) as exc:
        print(f"Gradle module inventory error: {exc}", file=sys.stderr)
        return 1

    for module in modules:
        print(module)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
