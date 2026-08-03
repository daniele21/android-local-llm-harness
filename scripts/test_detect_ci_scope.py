#!/usr/bin/env python3

from __future__ import annotations

import unittest

from detect_ci_scope import classify_paths


class DetectCiScopeTest(unittest.TestCase):
    def test_documentation_only_skips_expensive_jobs(self) -> None:
        scope = classify_paths(["README.md", "docs/architecture.md"])
        self.assertFalse(scope.android)
        self.assertFalse(scope.native)

    def test_dependabot_configuration_is_metadata_only(self) -> None:
        scope = classify_paths([".github/dependabot.yml"])
        self.assertFalse(scope.android)
        self.assertFalse(scope.native)

    def test_kotlin_change_runs_android_only(self) -> None:
        scope = classify_paths(["core/runtime-core/src/main/kotlin/Runtime.kt"])
        self.assertTrue(scope.android)
        self.assertFalse(scope.native)

    def test_mixed_documentation_and_code_runs_android(self) -> None:
        scope = classify_paths(["README.md", "core/contracts/src/main/kotlin/Request.kt"])
        self.assertTrue(scope.android)
        self.assertFalse(scope.native)

    def test_native_change_runs_both_expensive_jobs(self) -> None:
        scope = classify_paths(["backends/llama-cpp/src/main/cpp/runtime.cpp"])
        self.assertTrue(scope.android)
        self.assertTrue(scope.native)

    def test_windows_native_path_is_normalized(self) -> None:
        scope = classify_paths([r"backends\llama-cpp\src\main\cpp\runtime.cpp"])
        self.assertTrue(scope.android)
        self.assertTrue(scope.native)

    def test_submodule_pin_change_runs_both_expensive_jobs(self) -> None:
        scope = classify_paths(["third_party/llama.cpp"])
        self.assertTrue(scope.android)
        self.assertTrue(scope.native)

    def test_validation_workflow_change_forces_full_validation(self) -> None:
        scope = classify_paths([".github/workflows/validate.yml"])
        self.assertTrue(scope.android)
        self.assertTrue(scope.native)

    def test_empty_diff_fails_safe(self) -> None:
        scope = classify_paths([])
        self.assertTrue(scope.android)
        self.assertTrue(scope.native)


if __name__ == "__main__":
    unittest.main()
