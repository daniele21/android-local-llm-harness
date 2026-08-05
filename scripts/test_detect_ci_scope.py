#!/usr/bin/env python3

from __future__ import annotations

import unittest

from detect_ci_scope import adjust_for_event, classify_paths


class DetectCiScopeTest(unittest.TestCase):
    def test_documentation_only_skips_expensive_jobs(self) -> None:
        scope = classify_paths(["README.md", "docs/architecture.md"])
        self.assertFalse(scope.android)
        self.assertFalse(scope.native)
        self.assertFalse(scope.packaging)
        self.assertEqual(scope.modules, ())

    def test_dependabot_configuration_is_metadata_only(self) -> None:
        scope = classify_paths([".github/dependabot.yml"])
        self.assertFalse(scope.android)
        self.assertFalse(scope.native)
        self.assertFalse(scope.packaging)
        self.assertEqual(scope.modules, ())

    def test_core_kotlin_change_selects_only_changed_module(self) -> None:
        scope = classify_paths(["core/runtime-core/src/main/kotlin/Runtime.kt"])
        self.assertTrue(scope.android)
        self.assertFalse(scope.native)
        self.assertFalse(scope.packaging)
        self.assertEqual(scope.modules, ("core:runtime-core",))

    def test_model_download_change_selects_only_downloader_module(self) -> None:
        scope = classify_paths(
            ["models/model-download/src/main/kotlin/SecureModelDownloader.kt"]
        )
        self.assertTrue(scope.android)
        self.assertFalse(scope.native)
        self.assertFalse(scope.packaging)
        self.assertEqual(scope.modules, ("models:model-download",))

    def test_app_change_selects_app_and_runs_packaging(self) -> None:
        scope = classify_paths(["apps/local-llm-phone-test/src/main/kotlin/MainActivity.kt"])
        self.assertTrue(scope.android)
        self.assertFalse(scope.native)
        self.assertTrue(scope.packaging)
        self.assertEqual(scope.modules, ("apps:local-llm-phone-test",))

    def test_mixed_modules_preserve_repository_order(self) -> None:
        scope = classify_paths(
            [
                "observability/health-engine/src/main/kotlin/Health.kt",
                "core/contracts/src/main/kotlin/Request.kt",
            ]
        )
        self.assertEqual(
            scope.modules,
            ("core:contracts", "observability:health-engine"),
        )

    def test_mixed_documentation_and_core_code_runs_android_only(self) -> None:
        scope = classify_paths(["README.md", "core/contracts/src/main/kotlin/Request.kt"])
        self.assertTrue(scope.android)
        self.assertFalse(scope.native)
        self.assertFalse(scope.packaging)
        self.assertEqual(scope.modules, ("core:contracts",))

    def test_native_change_selects_backend_and_runs_all_validation(self) -> None:
        scope = classify_paths(["backends/llama-cpp/src/main/cpp/runtime.cpp"])
        self.assertTrue(scope.android)
        self.assertTrue(scope.native)
        self.assertTrue(scope.packaging)
        self.assertEqual(scope.modules, ("backends:llama-cpp",))

    def test_windows_native_path_is_normalized(self) -> None:
        scope = classify_paths([r"backends\llama-cpp\src\main\cpp\runtime.cpp"])
        self.assertTrue(scope.android)
        self.assertTrue(scope.native)
        self.assertTrue(scope.packaging)
        self.assertEqual(scope.modules, ("backends:llama-cpp",))

    def test_submodule_pin_change_selects_backend(self) -> None:
        scope = classify_paths(["third_party/llama.cpp"])
        self.assertTrue(scope.android)
        self.assertTrue(scope.native)
        self.assertTrue(scope.packaging)
        self.assertEqual(scope.modules, ("backends:llama-cpp",))

    def test_build_configuration_change_runs_all_modules(self) -> None:
        scope = classify_paths(["gradle/libs.versions.toml"])
        self.assertTrue(scope.android)
        self.assertFalse(scope.native)
        self.assertTrue(scope.packaging)
        self.assertEqual(scope.modules, ("all",))

    def test_manifest_change_in_known_module_selects_that_module(self) -> None:
        scope = classify_paths(
            ["apps/local-llm-console/src/main/AndroidManifest.xml"]
        )
        self.assertTrue(scope.android)
        self.assertFalse(scope.native)
        self.assertTrue(scope.packaging)
        self.assertEqual(scope.modules, ("apps:local-llm-console",))

    def test_unknown_implementation_path_fails_safe_to_all_modules(self) -> None:
        scope = classify_paths(["new-module/src/main/kotlin/NewFeature.kt"])
        self.assertTrue(scope.android)
        self.assertEqual(scope.modules, ("all",))

    def test_validation_workflow_change_forces_full_validation(self) -> None:
        scope = classify_paths([".github/workflows/validate.yml"])
        self.assertTrue(scope.android)
        self.assertTrue(scope.native)
        self.assertTrue(scope.packaging)
        self.assertEqual(scope.modules, ("all",))

    def test_package_workflow_change_forces_full_validation(self) -> None:
        scope = classify_paths([".github/workflows/package.yml"])
        self.assertTrue(scope.android)
        self.assertTrue(scope.native)
        self.assertTrue(scope.packaging)
        self.assertEqual(scope.modules, ("all",))

    def test_push_delegates_packaging_but_preserves_modules(self) -> None:
        scope = adjust_for_event(classify_paths(["apps/example/Main.kt"]), "push")
        self.assertTrue(scope.android)
        self.assertFalse(scope.native)
        self.assertFalse(scope.packaging)
        self.assertEqual(scope.modules, ("all",))

    def test_empty_diff_fails_safe(self) -> None:
        scope = classify_paths([])
        self.assertTrue(scope.android)
        self.assertTrue(scope.native)
        self.assertTrue(scope.packaging)
        self.assertEqual(scope.modules, ("all",))


if __name__ == "__main__":
    unittest.main()
