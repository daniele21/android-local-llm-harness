#!/usr/bin/env python3
"""Synchronize presentation PNGs from the canonical H Bridge Core masters."""

from __future__ import annotations

import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "assets" / "brand"
MASTER = OUT / "master"

SYMBOL = MASTER / "harness-symbol.png"
LOCKUP_LIGHT = MASTER / "harness-lockup-light.png"
LOCKUP_DARK = MASTER / "harness-lockup-dark.png"
APP_ICON_LIGHT = MASTER / "harness-app-icon-light.png"
APP_ICON_DARK = MASTER / "harness-app-icon-dark.png"

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def require_png(path: Path) -> None:
    if not path.is_file():
        raise SystemExit(f"Missing canonical brand PNG: {path.relative_to(ROOT)}")
    if path.read_bytes()[:8] != PNG_SIGNATURE:
        raise SystemExit(f"Brand master is not a PNG: {path.relative_to(ROOT)}")


def copy(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)
    print(f"Wrote {destination.relative_to(ROOT)}")


def main() -> None:
    for master in (SYMBOL, LOCKUP_LIGHT, LOCKUP_DARK, APP_ICON_LIGHT, APP_ICON_DARK):
        require_png(master)

    outputs = {
        OUT / "light" / "symbol.png": SYMBOL,
        OUT / "light" / "logo-lockup.png": LOCKUP_LIGHT,
        OUT / "light" / "app-icon.png": APP_ICON_LIGHT,
        OUT / "light" / "favicon.png": APP_ICON_LIGHT,
        OUT / "dark" / "symbol.png": SYMBOL,
        OUT / "dark" / "logo-lockup.png": LOCKUP_DARK,
        OUT / "dark" / "app-icon.png": APP_ICON_DARK,
        OUT / "dark" / "favicon.png": APP_ICON_DARK,
    }
    for destination, source in outputs.items():
        copy(source, destination)


if __name__ == "__main__":
    main()
