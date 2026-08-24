#!/usr/bin/env python3
"""Fail when generated Android/Gradle artifacts are tracked by Git."""

from __future__ import annotations

import argparse
from pathlib import Path, PurePosixPath
import subprocess
import sys

FORBIDDEN_DIRECTORY_NAMES = frozenset(
    {
        "build",
        ".gradle",
        ".cxx",
        ".externalNativeBuild",
    }
)
FORBIDDEN_FILE_SUFFIXES = (".apk", ".aab")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    return parser.parse_args()


def is_forbidden_tracked_path(path: str) -> bool:
    """Return whether a tracked repository path is a generated build artifact."""
    posix_path = PurePosixPath(path)
    if any(part in FORBIDDEN_DIRECTORY_NAMES for part in posix_path.parts):
        return True
    return posix_path.name.endswith(FORBIDDEN_FILE_SUFFIXES)


def tracked_files(root: Path) -> list[str]:
    """Return tracked paths exactly as recorded in the repository index."""
    result = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z"],
        check=True,
        capture_output=True,
    )
    return [
        raw.decode("utf-8", errors="surrogateescape")
        for raw in result.stdout.split(b"\0")
        if raw
    ]


def find_forbidden_tracked_files(root: Path) -> list[str]:
    return sorted(path for path in tracked_files(root) if is_forbidden_tracked_path(path))


def main() -> int:
    args = parse_args()
    root = Path(args.root).resolve()

    print("Tracked generated-artifact check")
    print(f"root: {root}")

    try:
        forbidden = find_forbidden_tracked_files(root)
    except (OSError, subprocess.CalledProcessError) as exc:
        print(f"FAIL: unable to inspect Git index: {exc}")
        return 1

    if forbidden:
        print("FAIL: generated build artifacts are tracked by Git:")
        for path in forbidden:
            print(f"  {path}")
        print(
            "Remove generated paths from the index while keeping local outputs ignored; "
            "for example: git rm -r --cached -- <generated-path>"
        )
        return 1

    print("RESULT: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
