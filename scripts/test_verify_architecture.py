#!/usr/bin/env python3

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from unittest import mock

import verify_architecture
from verify_architecture import (
    CppIncludeViolation,
    DependencyViolation,
    evaluate_cpp_include_violations,
    evaluate_dependency_violations,
    find_cpp_include_violations,
    find_dependency_violations,
)


class ArchitectureFitnessTest(unittest.TestCase):
    def test_core_backend_dependency_is_detected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            module = root / "core" / "runtime-core"
            module.mkdir(parents=True)
            (module / "build.gradle.kts").write_text(
                'dependencies { implementation(project(":backends:llama-cpp")) }\n',
                encoding="utf-8",
            )
            self.assertEqual(
                find_dependency_violations(root, ("core:runtime-core",)),
                (DependencyViolation("core:runtime-core", "backends:llama-cpp", "implementation"),),
            )

    def test_test_dependency_does_not_violate_production_direction(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            module = root / "core" / "runtime-core"
            module.mkdir(parents=True)
            (module / "build.gradle.kts").write_text(
                'dependencies { testImplementation(project(":backends:fake")) }\n',
                encoding="utf-8",
            )
            self.assertEqual(find_dependency_violations(root, ("core:runtime-core",)), ())

    def test_any_core_backend_dependency_fails_without_exception(self) -> None:
        violation = DependencyViolation("core:runtime-core", "backends:llama-cpp", "implementation")
        messages = evaluate_dependency_violations((violation,))
        self.assertTrue(any("core:runtime-core -> backends:llama-cpp" in message for message in messages))

    def test_empty_dependency_exception_set_has_no_stale_debt(self) -> None:
        self.assertEqual(evaluate_dependency_violations(()), ())

    def test_cpp_implementation_include_is_detected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "native" / "entry.cpp"
            source.parent.mkdir(parents=True)
            source.write_text('#include "implementation.cpp"\n', encoding="utf-8")
            self.assertEqual(
                find_cpp_include_violations(root),
                (CppIncludeViolation("native/entry.cpp", "implementation.cpp"),),
            )

    def test_unexpected_cpp_include_fails(self) -> None:
        violation = CppIncludeViolation("native/entry.cpp", "implementation.cpp")
        messages = evaluate_cpp_include_violations((violation,))
        self.assertTrue(any("implementation .cpp include is forbidden" in message for message in messages))

    def test_known_cpp_debt_is_allowed(self) -> None:
        violation = CppIncludeViolation(
            "backends/llama-cpp/src/main/cpp/llama_jni_entry.cpp",
            "llama_jni.cpp",
        )
        self.assertEqual(evaluate_cpp_include_violations((violation,)), ())

    def test_removed_cpp_debt_requires_exception_cleanup(self) -> None:
        messages = evaluate_cpp_include_violations(())
        self.assertTrue(any("stale native architecture exception" in message for message in messages))

    def test_third_party_cpp_is_not_scanned(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "third_party" / "vendor" / "entry.cpp"
            source.parent.mkdir(parents=True)
            source.write_text('#include "implementation.cpp"\n', encoding="utf-8")
            self.assertEqual(find_cpp_include_violations(root), ())

    def test_evaluators_can_be_used_with_explicit_exception_sets(self) -> None:
        dependency = DependencyViolation("core:a", "backends:b", "implementation")
        cpp = CppIncludeViolation("native/a.cpp", "b.cpp")
        with mock.patch.object(verify_architecture, "KNOWN_DEPENDENCY_EXCEPTIONS", {}), mock.patch.object(
            verify_architecture, "KNOWN_CPP_INCLUDE_EXCEPTIONS", {}
        ):
            self.assertEqual(len(evaluate_dependency_violations((dependency,))), 1)
            self.assertEqual(len(evaluate_cpp_include_violations((cpp,))), 1)


if __name__ == "__main__":
    unittest.main()
