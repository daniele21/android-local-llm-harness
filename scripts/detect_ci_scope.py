#!/usr/bin/env python3
"""Determine which validation jobs and Gradle modules are required."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from dataclasses import dataclass
from pathlib import PurePosixPath
from typing import Iterable, Sequence

ZERO_SHA = "0" * 40

DOC_ONLY_PATHS = {
    ".github/CODEOWNERS",
    ".github/dependabot.yml",
    ".gitattributes",
    ".gitignore",
    "CODE_OF_CONDUCT.md",
    "CONTRIBUTING.md",
    "LICENSE",
    "NOTICE",
    "README.md",
    "SECURITY.md",
}
DOC_ONLY_PREFIXES = ("docs/",)
DOC_ONLY_SUFFIXES = (".md", ".mdx", ".rst")

FORCE_ALL_PATHS = {
    ".github/workflows/package.yml",
    ".github/workflows/validate.yml",
    "scripts/detect_ci_scope.py",
    "scripts/test_detect_ci_scope.py",
}

NATIVE_PATHS = {
    ".gitmodules",
    "third_party/llama.cpp",
    "scripts/test-verify-llama-cpp-pin.sh",
    "scripts/verify-android-packaging.py",
    "scripts/verify-llama-cpp-pin.sh",
}
NATIVE_PREFIXES = (
    "backends/llama-cpp/",
    "third_party/llama.cpp/",
)
NATIVE_SUFFIXES = (
    ".c",
    ".cc",
    ".cmake",
    ".cpp",
    ".cxx",
    ".h",
    ".hh",
    ".hpp",
    ".hxx",
)

PACKAGING_PATHS = {
    ".gitmodules",
    "build.gradle.kts",
    "gradle.properties",
    "settings.gradle.kts",
    "scripts/verify-android-packaging.py",
    "third_party/llama.cpp",
}
PACKAGING_PREFIXES = (
    "apps/",
    "backends/llama-cpp/",
    "gradle/",
    "third_party/llama.cpp/",
)
PACKAGING_SUFFIXES = (
    ".gradle",
    ".gradle.kts",
)

GRADLE_MODULES = (
    "core:contracts",
    "core:runtime-core",
    "models:model-profile",
    "models:model-store",
    "backends:llama-cpp",
    "observability:contracts",
    "observability:in-memory-store",
    "observability:room-store",
    "observability:health-engine",
    "observability:android-resource-probe",
    "observability:benchmark-engine",
    "transports:in-process",
    "apps:local-llm-console",
    "apps:device-test-runner",
    "apps:local-llm-phone-test",
)
MODULE_PREFIXES = tuple(
    (module.replace(":", "/") + "/", module) for module in GRADLE_MODULES
)
GLOBAL_GRADLE_PATHS = {
    "build.gradle.kts",
    "gradle.properties",
    "settings.gradle.kts",
}
GLOBAL_GRADLE_PREFIXES = ("build-logic/", "gradle/")


@dataclass(frozen=True)
class ValidationScope:
    android: bool
    native: bool
    packaging: bool
    modules: tuple[str, ...]
    reason: str


def normalize_path(path: str) -> str:
    return str(PurePosixPath(path.strip().replace("\\", "/")))


def is_docs_only(path: str) -> bool:
    return (
        path in DOC_ONLY_PATHS
        or path.startswith(DOC_ONLY_PREFIXES)
        or path.endswith(DOC_ONLY_SUFFIXES)
    )


def affects_native(path: str) -> bool:
    filename = PurePosixPath(path).name
    return (
        path in NATIVE_PATHS
        or path.startswith(NATIVE_PREFIXES)
        or path.endswith(NATIVE_SUFFIXES)
        or filename == "CMakeLists.txt"
    )


def affects_packaging(path: str) -> bool:
    filename = PurePosixPath(path).name
    return (
        affects_native(path)
        or path in PACKAGING_PATHS
        or path.startswith(PACKAGING_PREFIXES)
        or path.endswith(PACKAGING_SUFFIXES)
        or filename == "AndroidManifest.xml"
    )


def affected_gradle_modules(paths: Sequence[str]) -> tuple[str, ...]:
    implementation_paths = tuple(path for path in paths if not is_docs_only(path))
    if not implementation_paths:
        return ()

    if any(
        path in FORCE_ALL_PATHS
        or path in GLOBAL_GRADLE_PATHS
        or path.startswith(GLOBAL_GRADLE_PREFIXES)
        for path in implementation_paths
    ):
        return ("all",)

    modules: set[str] = set()
    unresolved: list[str] = []
    for path in implementation_paths:
        matched = False
        for prefix, module in MODULE_PREFIXES:
            module_root = prefix.removesuffix("/")
            if path == module_root or path.startswith(prefix):
                modules.add(module)
                matched = True
                break

        if matched:
            continue

        if path == "third_party/llama.cpp" or path.startswith("third_party/llama.cpp/"):
            modules.add("backends:llama-cpp")
        elif path in NATIVE_PATHS:
            modules.add("backends:llama-cpp")
        else:
            unresolved.append(path)

    # Unknown implementation or repository paths fail safe. This prevents a
    # new module or build convention from silently escaping validation.
    if unresolved:
        return ("all",)

    return tuple(module for module in GRADLE_MODULES if module in modules)


def classify_paths(paths: Iterable[str], *, force_all: bool = False) -> ValidationScope:
    normalized = tuple(sorted({normalize_path(path) for path in paths if path.strip()}))

    if force_all:
        return ValidationScope(
            android=True,
            native=True,
            packaging=True,
            modules=("all",),
            reason="manual validation",
        )

    if not normalized:
        return ValidationScope(
            android=True,
            native=True,
            packaging=True,
            modules=("all",),
            reason="no reliable diff available",
        )

    if any(path in FORCE_ALL_PATHS for path in normalized):
        return ValidationScope(
            android=True,
            native=True,
            packaging=True,
            modules=("all",),
            reason="validation infrastructure changed",
        )

    native = any(affects_native(path) for path in normalized)
    android = native or any(not is_docs_only(path) for path in normalized)
    packaging = android and any(affects_packaging(path) for path in normalized)
    modules = affected_gradle_modules(normalized) if android else ()

    if android and not modules:
        modules = ("all",)

    if native:
        reason = "native or JNI inputs changed"
    elif packaging:
        reason = "Android packaging inputs changed"
    elif android:
        reason = "Android or repository implementation changed"
    else:
        reason = "documentation or repository metadata only"

    return ValidationScope(
        android=android,
        native=native,
        packaging=packaging,
        modules=modules,
        reason=reason,
    )


def adjust_for_event(scope: ValidationScope, event_name: str) -> ValidationScope:
    if event_name == "push" and scope.packaging:
        return ValidationScope(
            android=scope.android,
            native=scope.native,
            packaging=False,
            modules=scope.modules,
            reason=f"{scope.reason}; packaging delegated to package workflow",
        )
    return scope


def ensure_commit_available(sha: str) -> None:
    if not sha or sha == ZERO_SHA:
        raise ValueError("missing or unusable comparison SHA")

    present = subprocess.run(
        ["git", "cat-file", "-e", f"{sha}^{{commit}}"],
        check=False,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    if present.returncode == 0:
        return

    subprocess.run(
        ["git", "fetch", "--no-tags", "--depth=1", "origin", sha],
        check=True,
    )


def git_changed_files(base_sha: str, head_sha: str) -> Sequence[str]:
    ensure_commit_available(base_sha)
    ensure_commit_available(head_sha)

    result = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=ACMRD", base_sha, head_sha],
        check=True,
        capture_output=True,
        text=True,
    )
    return tuple(line for line in result.stdout.splitlines() if line.strip())


def write_outputs(path: str, scope: ValidationScope) -> None:
    with open(path, "a", encoding="utf-8") as output:
        output.write(f"android={'true' if scope.android else 'false'}\n")
        output.write(f"native={'true' if scope.native else 'false'}\n")
        output.write(f"packaging={'true' if scope.packaging else 'false'}\n")
        output.write(f"modules={','.join(scope.modules)}\n")


def append_step_summary(paths: Sequence[str], scope: ValidationScope) -> None:
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not summary_path:
        return

    modules = ", ".join(scope.modules) if scope.modules else "none"
    with open(summary_path, "a", encoding="utf-8") as summary:
        summary.write("## Validation scope\n\n")
        summary.write(f"- Android validation: **{scope.android}**\n")
        summary.write(f"- Native host tests: **{scope.native}**\n")
        summary.write(f"- Android packaging verification: **{scope.packaging}**\n")
        summary.write(f"- Gradle modules: `{modules}`\n")
        summary.write(f"- Reason: {scope.reason}\n")
        summary.write(f"- Changed paths considered: {len(paths)}\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--event", required=True)
    parser.add_argument("--base-sha", default="")
    parser.add_argument("--head-sha", default="")
    parser.add_argument("--github-output", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    paths: Sequence[str] = ()

    if args.event == "workflow_dispatch":
        scope = classify_paths((), force_all=True)
    else:
        try:
            paths = git_changed_files(args.base_sha, args.head_sha)
            scope = adjust_for_event(classify_paths(paths), args.event)
        except (ValueError, subprocess.CalledProcessError) as exc:
            print(f"warning: unable to determine changed files: {exc}", file=sys.stderr)
            scope = adjust_for_event(classify_paths(()), args.event)

    write_outputs(args.github_output, scope)
    append_step_summary(paths, scope)

    print(
        "validation scope: "
        f"android={str(scope.android).lower()} "
        f"native={str(scope.native).lower()} "
        f"packaging={str(scope.packaging).lower()} "
        f"modules={','.join(scope.modules)} "
        f"reason={scope.reason}"
    )
    if paths:
        print("changed paths:")
        for path in paths:
            print(f"  - {path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
