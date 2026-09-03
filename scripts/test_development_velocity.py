#!/usr/bin/env python3
from __future__ import annotations
import unittest
from detect_ci_scope import classify_paths, gates_for

class DevelopmentVelocityTest(unittest.TestCase):
    def test_scoped_iteration_stays_fast(self) -> None:
        scope = classify_paths(["models/model-download/src/main/kotlin/Download.kt"])
        gates = gates_for(scope, "iteration")
        self.assertIn("affected-compile", gates)
        self.assertIn("affected-unit", gates)
        self.assertNotIn("affected-lint", gates)
        self.assertNotIn("android-packaging", gates)

    def test_binder_integration_adds_only_required_cross_boundary_gates(self) -> None:
        scope = classify_paths(["transports/android-binder-contract/src/main/kotlin/ProtocolModels.kt"])
        gates = gates_for(scope, "integration")
        self.assertIn("direct-contract", gates)
        self.assertIn("affected-lint", gates)
        self.assertIn("android-packaging", gates)

    def test_native_packaging_is_deferred_until_integration(self) -> None:
        scope = classify_paths(["backends/llama-cpp/src/main/cpp/runtime.cpp"])
        self.assertNotIn("native-host", gates_for(scope, "iteration"))
        self.assertNotIn("android-packaging", gates_for(scope, "iteration"))
        self.assertIn("native-host", gates_for(scope, "integration"))
        self.assertIn("android-packaging", gates_for(scope, "integration"))

if __name__ == "__main__":
    unittest.main()
