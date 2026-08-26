#!/usr/bin/env python3
"""Determine blast-radius validation profile, jobs and Gradle modules."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from dataclasses import dataclass, replace
from pathlib import PurePosixPath
from typing import Iterable, Sequence

from gradle_module_inventory import load_gradle_modules

ZERO_SHA = "0" * 40
PROFILE_RANK = {"lean": 0, "scoped": 1, "strong": 2, "full": 3}

DOC_ONLY_PATHS = {
    ".engineering/baseline.json",
    ".engineering/documentation-policy.json",
    ".github/CODEOWNERS",
    ".github/dependabot.yml",
    ".gitattributes",
    ".gitignore",
    "AGENTS.md",
    "CODE_OF_CONDUCT.md",
    "CONTRIBUTING.md",
    "EXECUTION-CAPABILITY-CONTRACT.md",
    "LICENSE",
    "NOTICE",
    "README.md",
    "SECURITY.md",
    "design/brand-kit.json",
    "design/ux-contract.json",
}
DOC_ONLY_PREFIXES = ("docs/",)
DOC_ONLY_SUFFIXES = (".md", ".mdx", ".rst")

# Changes to the machinery that decides what gets skipped cannot safely
# validate themselves with a narrowed profile.
FORCE_ALL_PATHS = {
    ".engineering/commands.json",
    ".github/workflows/package.yml",
    ".github/workflows/validate.yml",
    ".github/workflows/remote-preflight.yml",
    "scripts/detect_ci_scope.py",
    "scripts/gradle_module_inventory.py",
    "scripts/test_detect_ci_scope.py",
    "scripts/test_gradle_module_inventory.py",
}

NATIVE_PATHS = {
    ".gitmodules",
    "third_party/llama.cpp",
    "scripts/test-verify-llama-cpp-pin.sh",
    "scripts/verify-android-packaging.py",
    "scripts/verify-llama-cpp-pin.sh",
}
NATIVE_PREFIXES = ("backends/llama-cpp/", "third_party/llama.cpp/")
NATIVE_SUFFIXES = (".c", ".cc", ".cmake", ".cpp", ".cxx", ".h", ".hh", ".hpp", ".hxx")

PACKAGING_PATHS = {
    ".gitmodules",
    "build.gradle.kts",
    "gradle.properties",
    "settings.gradle.kts",
    "scripts/verify-android-packaging.py",
    "third_party/llama.cpp",
}
PACKAGING_PREFIXES = ("apps/", "backends/llama-cpp/", "gradle/", "third_party/llama.cpp/")
PACKAGE_BOUNDARY_PREFIXES = (
    "transports/android-binder-contract/",
    "transports/android-binder-client/",
    "integrations/android-service-host/",
    "apps/shared-runtime-client-consumer-fixture/",
)
PACKAGING_SUFFIXES = (".gradle", ".gradle.kts")

GRADLE_MODULES = load_gradle_modules()
MODULE_PREFIXES = tuple((module.replace(":", "/") + "/", module) for module in GRADLE_MODULES)
GLOBAL_GRADLE_PATHS = {"build.gradle.kts", "gradle.properties", "settings.gradle.kts", "gradle/libs.versions.toml"}
GLOBAL_GRADLE_PREFIXES = ("build-logic/", "gradle/")
PUBLIC_CONTRACT_PREFIXES = ("core/contracts/", "evaluation/contracts/", "observability/contracts/")
CROSS_BOUNDARY_PREFIXES = (
    "core/backend-spi/",
    "core/runtime-core/",
    "models/model-store/",
    "transports/android-binder-contract/",
    "transports/android-binder-client/",
    "integrations/android-service-host/",
    "apps/shared-runtime-client-consumer-fixture/",
    "evaluation/room-store/",
    "observability/room-store/",
)


@dataclass(frozen=True)
class ValidationScope:
    profile: str
    android: bool
    native: bool
    packaging: bool
    modules: tuple[str, ...]
    reason: str


def normalize_path(path: str) -> str:
    return str(PurePosixPath(path.strip().replace("\\", "/")))


def is_docs_only(path: str) -> bool:
    return path in DOC_ONLY_PATHS or path.startswith(DOC_ONLY_PREFIXES) or path.endswith(DOC_ONLY_SUFFIXES)


def affects_native(path: str) -> bool:
    filename = PurePosixPath(path).name
    return path in NATIVE_PATHS or path.startswith(NATIVE_PREFIXES) or path.endswith(NATIVE_SUFFIXES) or filename == "CMakeLists.txt"


def affects_packaging(path: str) -> bool:
    filename = PurePosixPath(path).name
    return (
        affects_native(path)
        or path in PACKAGING_PATHS
        or path.startswith(PACKAGING_PREFIXES)
        or path.startswith(PACKAGE_BOUNDARY_PREFIXES)
        or path.endswith(PACKAGING_SUFFIXES)
        or filename == "AndroidManifest.xml"
        or filename.startswith("proguard")
    )


def is_global_build_path(path: str) -> bool:
    return path in GLOBAL_GRADLE_PATHS or path.startswith(GLOBAL_GRADLE_PREFIXES)


def is_release_sensitive(path: str) -> bool:
    filename = PurePosixPath(path).name
    return (
        affects_native(path)
        or path.startswith(PUBLIC_CONTRACT_PREFIXES)
        or path.startswith(CROSS_BOUNDARY_PREFIXES)
        or path.endswith(PACKAGING_SUFFIXES)
        or filename == "AndroidManifest.xml"
        or filename.startswith("proguard")
    )


def affected_gradle_modules(paths: Sequence[str]) -> tuple[str, ...]:
    implementation_paths = tuple(path for path in paths if not is_docs_only(path))
    if not implementation_paths:
        return ()

    if any(path.startswith(PUBLIC_CONTRACT_PREFIXES) for path in implementation_paths):
        return ("all",)

    if any(path in FORCE_ALL_PATHS or is_global_build_path(path) for path in implementation_paths):
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
        if path == "third_party/llama.cpp" or path.startswith("third_party/llama.cpp/") or path in NATIVE_PATHS:
            modules.add("backends:llama-cpp")
        elif path.startswith((".engineering/", "skills/", "docs/", "design/")) or path in DOC_ONLY_PATHS:
            # Governance is handled by repository guards; it does not imply an
            # Android module unless it changes validation routing (FORCE_ALL_PATHS).
            continue
        else:
            unresolved.append(path)

    if unresolved:
        return ("all",)
    return tuple(module for module in GRADLE_MODULES if module in modules)


def classify_paths(paths: Iterable[str], *, force_all: bool = False) -> ValidationScope:
    normalized = tuple(sorted({normalize_path(path) for path in paths if path.strip()}))

    if force_all:
        return ValidationScope("full", True, True, True, ("all",), "explicit full validation")
    if not normalized:
        return ValidationScope("full", True, True, True, ("all",), "no reliable diff available")
    if any(path in FORCE_ALL_PATHS for path in normalized):
        return ValidationScope("full", True, True, True, ("all",), "validation selector or workflow changed")
    if any(is_global_build_path(path) for path in normalized):
        return ValidationScope("full", True, True, True, ("all",), "global Gradle/toolchain or dependency inventory changed")

    implementation = tuple(path for path in normalized if not is_docs_only(path))
    if not implementation:
        return ValidationScope("lean", False, False, False, (), "documentation or repository metadata only")

    native = any(affects_native(path) for path in implementation)
    android = True
    packaging = any(affects_packaging(path) for path in implementation)
    modules = affected_gradle_modules(normalized)

    public_contract = any(path.startswith(PUBLIC_CONTRACT_PREFIXES) for path in implementation)
    cross_boundary = any(path.startswith(CROSS_BOUNDARY_PREFIXES) for path in implementation)
    release_sensitive = any(is_release_sensitive(path) for path in implementation)

    if modules == ("all",) and not public_contract:
        profile = "full"
        reason = "unknown or repository-wide executable scope"
    elif native:
        profile = "strong"
        reason = "native or JNI inputs changed"
    elif public_contract:
        profile = "strong"
        reason = "shared public contract changed"
    elif cross_boundary:
        profile = "strong"
        reason = "runtime, Binder, consumer, persistence or cross-boundary owner changed"
    elif release_sensitive:
        profile = "strong"
        reason = "release-sensitive Android build/manifest/package input changed"
    else:
        profile = "scoped"
        reason = "contained Android implementation change"

    if android and not modules:
        modules = ("all",)
        profile = "full"
        reason = "executable change has no trustworthy module mapping"

    return ValidationScope(profile, android, native, packaging, modules, reason)


def apply_requested_profile(scope: ValidationScope, requested: str) -> ValidationScope:
    requested = requested.lower()
    if requested == "auto":
        return scope
    if requested not in {"strong", "full"}:
        raise ValueError("requested profile must be auto, strong or full")
    if PROFILE_RANK[requested] <= PROFILE_RANK[scope.profile]:
        return scope
    if requested == "full":
        return ValidationScope("full", True, True, True, ("all",), f"explicit full override; auto={scope.profile}: {scope.reason}")
    return replace(scope, profile="strong", reason=f"explicit strong override; auto={scope.profile}: {scope.reason}")


def adjust_for_event(scope: ValidationScope, event_name: str) -> ValidationScope:
    if event_name == "push" and scope.packaging and scope.profile != "full":
        return replace(scope, packaging=False, reason=f"{scope.reason}; packaging delegated to package workflow on push")
    return scope


def ensure_commit_available(sha: str) -> None:
    if not sha or sha == ZERO_SHA:
        raise ValueError("missing or unusable comparison SHA")
    present = subprocess.run(["git", "cat-file", "-e", f"{sha}^{{commit}}"], check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if present.returncode == 0:
        return
    subprocess.run(["git", "fetch", "--no-tags", "--depth=1", "origin", sha], check=True)


def git_changed_files(base_sha: str, head_sha: str) -> Sequence[str]:
    ensure_commit_available(base_sha)
    ensure_commit_available(head_sha)
    result = subprocess.run(["git", "diff", "--name-only", "--diff-filter=ACMRD", base_sha, head_sha], check=True, capture_output=True, text=True)
    return tuple(line for line in result.stdout.splitlines() if line.strip())


def write_outputs(path: str, scope: ValidationScope) -> None:
    with open(path, "a", encoding="utf-8") as output:
        output.write(f"profile={scope.profile}\n")
        output.write(f"android={'true' if scope.android else 'false'}\n")
        output.write(f"native={'true' if scope.native else 'false'}\n")
        output.write(f"packaging={'true' if scope.packaging else 'false'}\n")
        output.write(f"modules={','.join(scope.modules)}\n")
        output.write(f"reason={scope.reason}\n")


def append_step_summary(paths: Sequence[str], scope: ValidationScope) -> None:
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not summary_path:
        return
    modules = ", ".join(scope.modules) if scope.modules else "none"
    with open(summary_path, "a", encoding="utf-8") as summary:
        summary.write("## Validation scope\n\n")
        summary.write(f"- Profile: **{scope.profile.upper()}**\n")
        summary.write(f"- Android validation: **{scope.android}**\n")
        summary.write(f"- Native host tests: **{scope.native}**\n")
        summary.write(f"- Packaging-sensitive: **{scope.packaging}**\n")
        summary.write(f"- Gradle modules: `{modules}`\n")
        summary.write(f"- Reason: {scope.reason}\n")
        summary.write(f"- Changed paths considered: {len(paths)}\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--event", required=True)
    parser.add_argument("--base-sha", default="")
    parser.add_argument("--head-sha", default="")
    parser.add_argument("--github-output", required=True)
    parser.add_argument("--profile", default="auto", choices=("auto", "strong", "full"))
    parser.add_argument("--force-full", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    paths: Sequence[str] = ()
    try:
        if args.force_full:
            scope = classify_paths((), force_all=True)
        else:
            paths = git_changed_files(args.base_sha, args.head_sha)
            scope = classify_paths(paths)
            scope = apply_requested_profile(scope, args.profile)
            scope = adjust_for_event(scope, args.event)
    except (ValueError, subprocess.CalledProcessError) as exc:
        print(f"warning: unable to determine safe validation scope: {exc}", file=sys.stderr)
        scope = classify_paths((), force_all=True)

    write_outputs(args.github_output, scope)
    append_step_summary(paths, scope)
    print(
        "validation scope: "
        f"profile={scope.profile} android={str(scope.android).lower()} native={str(scope.native).lower()} "
        f"packaging={str(scope.packaging).lower()} modules={','.join(scope.modules)} reason={scope.reason}"
    )
    if paths:
        print("changed paths:")
        for path in paths:
            print(f"  - {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
