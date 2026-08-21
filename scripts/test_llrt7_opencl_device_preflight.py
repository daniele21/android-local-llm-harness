#!/usr/bin/env python3

from __future__ import annotations

import unittest

from llrt7_opencl_device_preflight import DeviceFacts, classify_device


class Llrt7OpenClDevicePreflightTest(unittest.TestCase):
    def test_verified_adreno_with_loader_is_eligible(self) -> None:
        result = classify_device(
            DeviceFacts(
                model="reference-phone",
                soc="Snapdragon 8 Gen 3",
                board_platform="pineapple",
                gpu_renderer="Qualcomm, Adreno (TM) 750, OpenGL ES 3.2",
                opencl_library_path="/vendor/lib64/libOpenCL.so",
            )
        )
        self.assertEqual(result.verdict, "REPRESENTATIVE_DEVICE_READY")
        self.assertTrue(result.eligible_for_physical_qualification)

    def test_verified_adreno_without_loader_fails_closed(self) -> None:
        result = classify_device(
            DeviceFacts(
                model="reference-phone",
                soc="Snapdragon 8 Elite",
                board_platform="sun",
                gpu_renderer="Adreno 830",
                opencl_library_path=None,
            )
        )
        self.assertEqual(result.verdict, "OPENCL_LOADER_MISSING")
        self.assertFalse(result.eligible_for_physical_qualification)

    def test_non_verified_renderer_is_not_representative(self) -> None:
        result = classify_device(
            DeviceFacts(
                model="other-phone",
                soc="other-soc",
                board_platform="other-board",
                gpu_renderer="Mali-G68",
                opencl_library_path="/vendor/lib64/libOpenCL.so",
            )
        )
        self.assertEqual(result.verdict, "DEVICE_NOT_REPRESENTATIVE")
        self.assertFalse(result.eligible_for_physical_qualification)

    def test_unverified_adreno_does_not_inherit_support_claim(self) -> None:
        result = classify_device(
            DeviceFacts(
                model="older-phone",
                soc="older-soc",
                board_platform="older-board",
                gpu_renderer="Adreno (TM) 640",
                opencl_library_path="/vendor/lib64/libOpenCL.so",
            )
        )
        self.assertEqual(result.verdict, "DEVICE_NOT_REPRESENTATIVE")
        self.assertFalse(result.eligible_for_physical_qualification)


if __name__ == "__main__":
    unittest.main()
