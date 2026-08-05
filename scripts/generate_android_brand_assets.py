#!/usr/bin/env python3
"""Generate and verify Android launcher assets from the Harness SVG master."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]
MASTER_DIR = ROOT / "docs" / "assets" / "brand" / "master"
SYMBOL_MASTER = MASTER_DIR / "harness-symbol.svg"
WORDMARK_MASTER = MASTER_DIR / "harness-wordmark.svg"
LOCKUP_MASTER = MASTER_DIR / "harness-lockup.svg"
APP_DIR = ROOT / "apps" / "local-llm-phone-test"
RES_DIR = APP_DIR / "src" / "main" / "res"
MANIFEST = APP_DIR / "src" / "main" / "AndroidManifest.xml"

ANDROID_NS = "http://schemas.android.com/apk/res/android"
SVG_NS = "http://www.w3.org/2000/svg"
EXPECTED_PATHS = (
    "left-bracket",
    "right-bracket",
    "bridge-primary",
    "bridge-indigo",
    "bridge-blue",
    "bridge-secondary",
)
EXPECTED_COLORS = {
    "left-bracket": "#7C5CFC",
    "right-bracket": "#25C2A0",
    "bridge-primary": "#7C5CFC",
    "bridge-indigo": "#6467EB",
    "bridge-blue": "#3E97CD",
    "bridge-secondary": "#25C2A0",
}
LAUNCHER_BACKGROUND = "#0B0F14"
SAFE_ZONE_MIN = 21.0
SAFE_ZONE_MAX = 87.0
FOREGROUND_SCALE = 0.72
FOREGROUND_TRANSLATE = 18.0
NUMBER_PATTERN = re.compile(r"-?\d+(?:\.\d+)?")


class BrandAssetError(RuntimeError):
    """Raised when brand sources or generated resources are inconsistent."""


def android_color(svg_color: str) -> str:
    value = svg_color.upper()
    if not re.fullmatch(r"#[0-9A-F]{6}", value):
        raise BrandAssetError(f"Unsupported SVG color: {svg_color}")
    return f"#FF{value[1:]}"


def parse_master(path: Path, expected_view_box: str) -> ElementTree.Element:
    if not path.is_file():
        raise BrandAssetError(f"Missing brand master: {path.relative_to(ROOT)}")
    root = ElementTree.parse(path).getroot()
    if root.tag != f"{{{SVG_NS}}}svg":
        raise BrandAssetError(f"{path.name} is not an SVG document")
    if root.attrib.get("viewBox") != expected_view_box:
        raise BrandAssetError(
            f"{path.name} viewBox must be {expected_view_box}, "
            f"found {root.attrib.get('viewBox')!r}"
        )
    if root.findall(f".//{{{SVG_NS}}}text"):
        raise BrandAssetError(f"{path.name} must contain outlined paths, not text nodes")
    return root


def symbol_paths() -> list[tuple[str, str, str]]:
    root = parse_master(SYMBOL_MASTER, "0 0 100 100")
    paths: list[tuple[str, str, str]] = []
    for node in root.findall(f".//{{{SVG_NS}}}path"):
        path_id = node.attrib.get("id", "")
        fill = node.attrib.get("fill", "").upper()
        path_data = node.attrib.get("d", "")
        if path_id:
            paths.append((path_id, fill, path_data))

    path_ids = tuple(path_id for path_id, _, _ in paths)
    if path_ids != EXPECTED_PATHS:
        raise BrandAssetError(
            f"Unexpected symbol path order: {path_ids}; expected {EXPECTED_PATHS}"
        )
    for path_id, fill, path_data in paths:
        expected_fill = EXPECTED_COLORS[path_id]
        if fill != expected_fill:
            raise BrandAssetError(
                f"{path_id} uses {fill}; expected approved color {expected_fill}"
            )
        if not path_data:
            raise BrandAssetError(f"{path_id} has no path data")

    coordinates = [
        float(value)
        for _, _, path_data in paths
        for value in NUMBER_PATTERN.findall(path_data)
    ]
    transformed_min = min(coordinates) * FOREGROUND_SCALE + FOREGROUND_TRANSLATE
    transformed_max = max(coordinates) * FOREGROUND_SCALE + FOREGROUND_TRANSLATE
    if transformed_min < SAFE_ZONE_MIN or transformed_max > SAFE_ZONE_MAX:
        raise BrandAssetError(
            "Adaptive foreground exceeds the Android safe zone: "
            f"{transformed_min:.2f}..{transformed_max:.2f}"
        )
    return paths


def validate_vector_masters(paths: list[tuple[str, str, str]]) -> None:
    wordmark = parse_master(WORDMARK_MASTER, "0 0 470 120")
    wordmark_paths = wordmark.findall(f".//{{{SVG_NS}}}path")
    if len(wordmark_paths) != 1 or wordmark_paths[0].attrib.get("id") != "harness-wordmark":
        raise BrandAssetError("Wordmark master must contain one outlined harness-wordmark path")
    if not wordmark_paths[0].attrib.get("d"):
        raise BrandAssetError("Wordmark master path is empty")

    lockup = parse_master(LOCKUP_MASTER, "0 0 760 260")
    lockup_paths = {
        node.attrib.get("id", ""): node
        for node in lockup.findall(f".//{{{SVG_NS}}}path")
    }
    for path_id, fill, path_data in paths:
        lockup_id = f"lockup-{path_id}"
        node = lockup_paths.get(lockup_id)
        if node is None:
            raise BrandAssetError(f"Lockup master is missing {lockup_id}")
        if node.attrib.get("fill", "").upper() != fill or node.attrib.get("d") != path_data:
            raise BrandAssetError(f"Lockup symbol path {lockup_id} diverges from the symbol master")
    uses = lockup.findall(f".//{{{SVG_NS}}}use")
    wordmark_href = "harness-wordmark.svg#harness-wordmark"
    if len(uses) != 1 or uses[0].attrib.get("href") != wordmark_href:
        raise BrandAssetError("Lockup master must reference the canonical outlined wordmark")


def render_paths(paths: list[tuple[str, str, str]], monochrome: bool) -> str:
    rendered = []
    for path_id, fill, path_data in paths:
        color = "#FFFFFFFF" if monochrome else android_color(fill)
        android_name = path_id.replace("-", "_")
        rendered.append(
            "        <path\n"
            f'            android:name="{android_name}"\n'
            f'            android:fillColor="{color}"\n'
            f'            android:pathData="{path_data}" />'
        )
    return "\n".join(rendered)


def vector_drawable(paths: list[tuple[str, str, str]], monochrome: bool) -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="{ANDROID_NS}"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:name="harness_symbol"
        android:scaleX="{FOREGROUND_SCALE}"
        android:scaleY="{FOREGROUND_SCALE}"
        android:translateX="{FOREGROUND_TRANSLATE}"
        android:translateY="{FOREGROUND_TRANSLATE}">
{render_paths(paths, monochrome)}
    </group>
</vector>
"""


def legacy_vector(paths: list[tuple[str, str, str]], round_icon: bool) -> str:
    if round_icon:
        background_path = (
            "M54,4 C81.61,4 104,26.39 104,54 "
            "C104,81.61 81.61,104 54,104 "
            "C26.39,104 4,81.61 4,54 "
            "C4,26.39 26.39,4 54,4 Z"
        )
    else:
        background_path = (
            "M18,4 H90 C97.73,4 104,10.27 104,18 "
            "V90 C104,97.73 97.73,104 90,104 "
            "H18 C10.27,104 4,97.73 4,90 "
            "V18 C4,10.27 10.27,4 18,4 Z"
        )
    return f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="{ANDROID_NS}"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FF0B0F14"
        android:pathData="{background_path}" />
    <group
        android:name="harness_symbol"
        android:scaleX="0.64"
        android:scaleY="0.64"
        android:translateX="22"
        android:translateY="22">
{render_paths(paths, monochrome=False)}
    </group>
</vector>
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


def outputs() -> dict[Path, str]:
    paths = symbol_paths()
    validate_vector_masters(paths)
    return {
        RES_DIR / "drawable" / "harness_launcher_foreground.xml": vector_drawable(
            paths, monochrome=False
        ),
        RES_DIR / "drawable" / "harness_launcher_monochrome.xml": vector_drawable(
            paths, monochrome=True
        ),
        RES_DIR / "values" / "harness_launcher_colors.xml": colors_xml(),
        RES_DIR / "mipmap-anydpi" / "ic_launcher.xml": legacy_vector(
            paths, round_icon=False
        ),
        RES_DIR / "mipmap-anydpi" / "ic_launcher_round.xml": legacy_vector(
            paths, round_icon=True
        ),
        RES_DIR / "mipmap-anydpi-v26" / "ic_launcher.xml": adaptive_icon(
            include_monochrome=False
        ),
        RES_DIR / "mipmap-anydpi-v26" / "ic_launcher_round.xml": adaptive_icon(
            include_monochrome=False
        ),
        RES_DIR / "mipmap-anydpi-v33" / "ic_launcher.xml": adaptive_icon(
            include_monochrome=True
        ),
        RES_DIR / "mipmap-anydpi-v33" / "ic_launcher_round.xml": adaptive_icon(
            include_monochrome=True
        ),
    }


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
    failures: list[str] = []
    try:
        generated = outputs()
    except (BrandAssetError, ElementTree.ParseError) as error:
        print(f"Android brand asset verification failed: {error}", file=sys.stderr)
        return 1

    for path, expected in generated.items():
        relative = path.relative_to(ROOT)
        if not path.is_file():
            failures.append(f"Missing generated resource: {relative}")
            continue
        actual = path.read_text(encoding="utf-8")
        if actual != expected:
            failures.append(
                f"Generated resource is stale: {relative}; "
                "run python3 scripts/generate_android_brand_assets.py"
            )
    failures.extend(verify_manifest())
    if failures:
        for failure in failures:
            print(failure, file=sys.stderr)
        return 1

    print("Android brand assets are reproducible and manifest-linked.")
    print(
        "Adaptive foreground safe zone: "
        f"{SAFE_ZONE_MIN:.0f}..{SAFE_ZONE_MAX:.0f} in a 108x108 viewport."
    )
    return 0


def write_generated() -> int:
    try:
        generated = outputs()
    except (BrandAssetError, ElementTree.ParseError) as error:
        print(f"Android brand asset generation failed: {error}", file=sys.stderr)
        return 1
    for path, content in generated.items():
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
