#!/usr/bin/env python3
"""Tests for the tracked generated-artifact repository guard."""

from __future__ import annotations

from pathlib import Path
import subprocess
import tempfile

import verify_no_tracked_generated_artifacts as guard


def run(*args: str, cwd: Path) -> None:
    subprocess.run(args, cwd=cwd, check=True, capture_output=True)


def test_path_classification() -> None:
    forbidden = (
        "build/output.txt",
        "models/model-install/build/intermediates/file-map.txt",
        ".gradle/cache.bin",
        "apps/device-test-runner/.cxx/Debug/object.o",
        "native/.externalNativeBuild/state.json",
        "release/app-debug.apk",
        "release/app-release.aab",
    )
    allowed = (
        "build.gradle.kts",
        "buildSrc/src/main/kotlin/ConventionPlugin.kt",
        "docs/build-system.md",
        "apps/device-test-runner/src/main/AndroidManifest.xml",
    )

    for path in forbidden:
        assert guard.is_forbidden_tracked_path(path), path
    for path in allowed:
        assert not guard.is_forbidden_tracked_path(path), path


def test_git_index_scan() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        run("git", "init", "-q", cwd=root)

        source = root / "src" / "main.kt"
        source.parent.mkdir(parents=True)
        source.write_text("fun main() = Unit\n", encoding="utf-8")

        generated = root / "module" / "build" / "generated.txt"
        generated.parent.mkdir(parents=True)
        generated.write_text("generated\n", encoding="utf-8")

        (root / ".gitignore").write_text("**/build/\n", encoding="utf-8")

        run("git", "add", "src/main.kt", ".gitignore", cwd=root)
        run("git", "add", "-f", "module/build/generated.txt", cwd=root)

        assert guard.find_forbidden_tracked_files(root) == ["module/build/generated.txt"]


def main() -> int:
    test_path_classification()
    test_git_index_scan()
    print("Tracked generated-artifact guard tests: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
