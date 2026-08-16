#!/usr/bin/env python3
"""Enforce repository architecture rules that must remain machine-verifiable."""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Mapping

from gradle_module_inventory import REPOSITORY_ROOT, load_gradle_modules

_PRODUCTION_PROJECT_DEPENDENCY = re.compile(
    r"\b(api|implementation|compileOnly|runtimeOnly)\s*\(\s*project\s*\(\s*[\"'](:[^\"']+)[\"']\s*\)\s*\)"
)
_CPP_IMPLEMENTATION_INCLUDE = re.compile(r'^\s*#\s*include\s*["<]([^">]+\.cpp)[">]', re.MULTILINE)

# Temporary debt is explicit and self-expiring: when an owning slice removes the
# violation, this verifier fails until the stale exception is deleted here too.
KNOWN_DEPENDENCY_EXCEPTIONS: Mapping[tuple[str, str], str] = {
    ("core:runtime-core", "backends:llama-cpp"): "RA-1 backend dependency inversion",
}
KNOWN_CPP_INCLUDE_EXCEPTIONS: Mapping[tuple[str, str], str] = {
    (
        "backends/llama-cpp/src/main/cpp/llama_jni_entry.cpp",
        "llama_jni.cpp",
    ): "RA-0 native translation-unit cleanup",
}


@dataclass(frozen=True, order=True)
class DependencyViolation:
    source_module: str
    target_module: str
    configuration: str


@dataclass(frozen=True, order=True)
class CppIncludeViolation:
    source_path: str
    included_path: str


def module_build_file(repository_root: Path, module: str) -> Path:
    module_root = repository_root.joinpath(*module.split(":"))
    kotlin = module_root / "build.gradle.kts"
    return kotlin if kotlin.is_file() else module_root / "build.gradle"


def find_dependency_violations(repository_root: Path, modules: Iterable[str]) -> tuple[DependencyViolation, ...]:
    violations: list[DependencyViolation] = []
    for source in modules:
        if not source.startswith("core:"):
            continue
        build_file = module_build_file(repository_root, source)
        if not build_file.is_file():
            continue
        text = build_file.read_text(encoding="utf-8")
        for configuration, raw_target in _PRODUCTION_PROJECT_DEPENDENCY.findall(text):
            target = raw_target.removeprefix(":")
            if target.startswith("backends:"):
                violations.append(DependencyViolation(source, target, configuration))
    return tuple(sorted(violations))


def find_cpp_include_violations(repository_root: Path) -> tuple[CppIncludeViolation, ...]:
    violations: list[CppIncludeViolation] = []
    for path in repository_root.rglob("*.cpp"):
        relative = path.relative_to(repository_root).as_posix()
        if relative.startswith("third_party/") or "/build/" in f"/{relative}/":
            continue
        text = path.read_text(encoding="utf-8")
        for included in _CPP_IMPLEMENTATION_INCLUDE.findall(text):
            violations.append(CppIncludeViolation(relative, included))
    return tuple(sorted(violations))


def evaluate_dependency_violations(violations: Iterable[DependencyViolation]) -> tuple[str, ...]:
    actual = {(item.source_module, item.target_module) for item in violations}
    expected = set(KNOWN_DEPENDENCY_EXCEPTIONS)
    messages = [
        "forbidden production dependency: "
        f"{item.source_module} -> {item.target_module} via {item.configuration}"
        for item in violations
        if (item.source_module, item.target_module) not in expected
    ]
    for stale in sorted(expected - actual):
        messages.append(
            "stale architecture exception: "
            f"{stale[0]} -> {stale[1]} ({KNOWN_DEPENDENCY_EXCEPTIONS[stale]}); remove the exception"
        )
    return tuple(messages)


def evaluate_cpp_include_violations(violations: Iterable[CppIncludeViolation]) -> tuple[str, ...]:
    actual = {(item.source_path, item.included_path) for item in violations}
    expected = set(KNOWN_CPP_INCLUDE_EXCEPTIONS)
    messages = [
        f"implementation .cpp include is forbidden: {item.source_path} -> {item.included_path}"
        for item in violations
        if (item.source_path, item.included_path) not in expected
    ]
    for stale in sorted(expected - actual):
        messages.append(
            "stale native architecture exception: "
            f"{stale[0]} -> {stale[1]} ({KNOWN_CPP_INCLUDE_EXCEPTIONS[stale]}); remove the exception"
        )
    return tuple(messages)


def verify(repository_root: Path = REPOSITORY_ROOT) -> tuple[str, ...]:
    modules = load_gradle_modules(repository_root / "settings.gradle.kts")
    dependency_violations = find_dependency_violations(repository_root, modules)
    cpp_include_violations = find_cpp_include_violations(repository_root)
    return (
        *evaluate_dependency_violations(dependency_violations),
        *evaluate_cpp_include_violations(cpp_include_violations),
    )


def main() -> int:
    try:
        violations = verify()
    except (OSError, ValueError) as exc:
        print(f"Architecture verification error: {exc}", file=sys.stderr)
        return 1
    if violations:
        print("Architecture verification failed:", file=sys.stderr)
        for violation in violations:
            print(f"- {violation}", file=sys.stderr)
        return 1
    print(
        "Architecture verification passed "
        f"({len(KNOWN_DEPENDENCY_EXCEPTIONS)} dependency debt exception, "
        f"{len(KNOWN_CPP_INCLUDE_EXCEPTIONS)} native debt exception)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
