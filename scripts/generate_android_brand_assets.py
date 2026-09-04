#!/usr/bin/env python3
"""Generate and verify Android launcher resources from canonical Harnex PNG masters."""

from __future__ import annotations

import argparse
import shutil
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MASTER_DIR = ROOT / "docs" / "assets" / "brand" / "master"
SYMBOL_MASTER = MASTER_DIR / "harnex-symbol.png"
APP_ICON_MASTER = MASTER_DIR / "harnex-app-icon-dark.png"
APP_DIR = ROOT / "apps" / "local-llm-phone-test"
RES_DIR = APP_DIR / "src" / "main" / "res"
MANIFEST = APP_DIR / "src" / "main" / "AndroidManifest.xml"
SYMBOL_RESOURCE = RES_DIR / "drawable-nodpi" / "harness_launcher_symbol.png"
LEGACY_RESOURCE = RES_DIR / "drawable-nodpi" / "harness_launcher_legacy.png"
ANDROID_NS = "http://schemas.android.com/apk/res/android"
LAUNCHER_BACKGROUND = "#0B1633"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
EXPECTED_MASTER_SIZE = (1254, 1254)
FOREGROUND_SIZE_DP = 66


def png_size(path: Path) -> tuple[int, int]:
    if not path.is_file():
        raise RuntimeError(f"Missing PNG: {path.relative_to(ROOT)}")
    data = path.read_bytes()
    if len(data) < 24 or data[:8] != PNG_SIGNATURE or data[12:16] != b"IHDR":
        raise RuntimeError(f"Invalid PNG: {path.relative_to(ROOT)}")
    return struct.unpack(">II", data[16:24])


def foreground_drawable(monochrome: bool) -> str:
    tint = ' android:tint="#FFFFFFFF"' if monochrome else ""
    return f"""<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="{ANDROID_NS}">
    <item
        android:width="{FOREGROUND_SIZE_DP}dp"
        android:height="{FOREGROUND_SIZE_DP}dp"
        android:gravity="center">
        <bitmap
            android:src="@drawable/harness_launcher_symbol"
            android:gravity="fill"{tint} />
    </item>
</layer-list>
"""


def legacy_icon() -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<bitmap xmlns:android="{ANDROID_NS}"
    android:src="@drawable/harness_launcher_legacy"
    android:gravity="fill" />
"""


def adaptive_icon(include_monochrome: bool) -> str:
    monochrome = (
        '\n    <monochrome android:drawable="@drawable/harness_launcher_monochrome" />'
        if include_monochrome
        else ""
    )
    return f"""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="{ANDROID_NS}">
    <background android:drawable="@color/harness_launcher_background" />
    <foreground android:drawable="@drawable/harness_launcher_foreground" />{monochrome}
</adaptive-icon>
"""


def colors_xml() -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="harness_launcher_background">{LAUNCHER_BACKGROUND}</color>
</resources>
"""


def text_outputs() -> dict[Path, str]:
    return {
        RES_DIR / "drawable" / "harness_launcher_foreground.xml": foreground_drawable(False),
        RES_DIR / "drawable" / "harness_launcher_monochrome.xml": foreground_drawable(True),
        RES_DIR / "values" / "harness_launcher_colors.xml": colors_xml(),
        RES_DIR / "mipmap-anydpi" / "ic_launcher.xml": legacy_icon(),
        RES_DIR / "mipmap-anydpi" / "ic_launcher_round.xml": legacy_icon(),
        RES_DIR / "mipmap-anydpi-v26" / "ic_launcher.xml": adaptive_icon(False),
        RES_DIR / "mipmap-anydpi-v26" / "ic_launcher_round.xml": adaptive_icon(False),
        RES_DIR / "mipmap-anydpi-v33" / "ic_launcher.xml": adaptive_icon(True),
        RES_DIR / "mipmap-anydpi-v33" / "ic_launcher_round.xml": adaptive_icon(True),
    }


def verify_masters() -> list[str]:
    failures: list[str] = []
    for path in (SYMBOL_MASTER, APP_ICON_MASTER):
        try:
            size = png_size(path)
        except RuntimeError as error:
            failures.append(str(error))
            continue
        if size != EXPECTED_MASTER_SIZE:
            failures.append(
                f"Unexpected PNG size for {path.relative_to(ROOT)}: {size}; expected {EXPECTED_MASTER_SIZE}"
            )
    return failures


def verify_manifest() -> list[str]:
    if not MANIFEST.is_file():
        return [f"Missing manifest: {MANIFEST.relative_to(ROOT)}"]
    content = MANIFEST.read_text(encoding="utf-8")
    required = (
        'android:icon="@mipmap/ic_launcher"',
        'android:roundIcon="@mipmap/ic_launcher_round"',
    )
    return [f"Manifest is missing {value}" for value in required if value not in content]


def check_generated() -> int:
    failures = verify_masters()
    binary_outputs = {
        SYMBOL_RESOURCE: SYMBOL_MASTER,
        LEGACY_RESOURCE: APP_ICON_MASTER,
    }
    for destination, source in binary_outputs.items():
        if not destination.is_file():
            failures.append(f"Missing generated PNG: {destination.relative_to(ROOT)}")
        elif destination.read_bytes() != source.read_bytes():
            failures.append(
                f"Generated PNG is stale: {destination.relative_to(ROOT)}; run python3 scripts/generate_android_brand_assets.py"
            )
    for path, expected in text_outputs().items():
        if not path.is_file():
            failures.append(f"Missing generated resource: {path.relative_to(ROOT)}")
        elif path.read_text(encoding="utf-8") != expected:
            failures.append(
                f"Generated resource is stale: {path.relative_to(ROOT)}; run python3 scripts/generate_android_brand_assets.py"
            )
    failures.extend(verify_manifest())
    if failures:
        for failure in failures:
            print(failure, file=sys.stderr)
        return 1
    print("Android Harnex H Bridge Core PNG assets are reproducible and manifest-linked.")
    return 0


def write_generated() -> int:
    failures = verify_masters()
    if failures:
        for failure in failures:
            print(failure, file=sys.stderr)
        return 1
    SYMBOL_RESOURCE.parent.mkdir(parents=True, exist_ok=True)
    LEGACY_RESOURCE.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(SYMBOL_MASTER, SYMBOL_RESOURCE)
    shutil.copyfile(APP_ICON_MASTER, LEGACY_RESOURCE)
    for path, content in text_outputs().items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        print(f"Wrote {path.relative_to(ROOT)}")
    manifest_failures = verify_manifest()
    if manifest_failures:
        for failure in manifest_failures:
            print(failure, file=sys.stderr)
        return 1
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--check",
        action="store_true",
        help="Verify committed Android resources without modifying the repository.",
    )
    arguments = parser.parse_args()
    return check_generated() if arguments.check else write_generated()


if __name__ == "__main__":
    raise SystemExit(main())
