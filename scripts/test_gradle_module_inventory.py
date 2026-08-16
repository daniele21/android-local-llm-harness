#!/usr/bin/env python3

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from gradle_module_inventory import load_gradle_modules, validate_module_roots


class GradleModuleInventoryTest(unittest.TestCase):
    def test_reads_modules_in_settings_order(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            settings = Path(directory) / "settings.gradle.kts"
            settings.write_text(
                'rootProject.name = "sample"\ninclude(\n    ":core:contracts",\n    ":apps:demo",\n)\n',
                encoding="utf-8",
            )
            self.assertEqual(load_gradle_modules(settings), ("core:contracts", "apps:demo"))

    def test_rejects_duplicate_modules(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            settings = Path(directory) / "settings.gradle.kts"
            settings.write_text('include(":core:contracts", ":core:contracts")\n', encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Duplicate Gradle modules"):
                load_gradle_modules(settings)

    def test_rejects_empty_inventory(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            settings = Path(directory) / "settings.gradle.kts"
            settings.write_text('rootProject.name = "sample"\n', encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "No Gradle modules found"):
                load_gradle_modules(settings)

    def test_validates_module_build_roots(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            module = root / "core" / "contracts"
            module.mkdir(parents=True)
            (module / "build.gradle.kts").write_text("", encoding="utf-8")
            validate_module_roots(("core:contracts",), root)

    def test_reports_missing_module_build_root(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "core:missing"):
                validate_module_roots(("core:missing",), Path(directory))


if __name__ == "__main__":
    unittest.main()
