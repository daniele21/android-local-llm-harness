#!/usr/bin/env python3
"""Fail-closed LLRT-7 representative-device preflight for Android OpenCL evidence."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from dataclasses import asdict, dataclass
from typing import Callable, Sequence


VERIFIED_ANDROID_ADRENO = ("750", "830")
OPENCL_LIBRARY_CANDIDATES = (
    "/vendor/lib64/libOpenCL.so",
    "/system/vendor/lib64/libOpenCL.so",
    "/system/lib64/libOpenCL.so",
    "/odm/lib64/libOpenCL.so",
)


@dataclass(frozen=True)
class DeviceFacts:
    model: str
    soc: str
    board_platform: str
    gpu_renderer: str
    opencl_library_path: str | None


@dataclass(frozen=True)
class PreflightResult:
    verdict: str
    eligible_for_physical_qualification: bool
    reason: str
    facts: DeviceFacts


def classify_device(facts: DeviceFacts) -> PreflightResult:
    renderer = facts.gpu_renderer.strip()
    verified_gpu = any(
        re.search(rf"\bAdreno(?:\s*\(TM\))?\s*{gpu}\b", renderer, re.IGNORECASE)
        for gpu in VERIFIED_ANDROID_ADRENO
    )
    if not verified_gpu:
        return PreflightResult(
            verdict="DEVICE_NOT_REPRESENTATIVE",
            eligible_for_physical_qualification=False,
            reason=(
                "Pinned llama.cpp OpenCL evidence is limited to representative Android "
                "Adreno 750/830 devices; this renderer must not be used for a support claim"
            ),
            facts=facts,
        )
    if not facts.opencl_library_path:
        return PreflightResult(
            verdict="OPENCL_LOADER_MISSING",
            eligible_for_physical_qualification=False,
            reason="Representative Adreno detected, but no device OpenCL loader was found",
            facts=facts,
        )
    return PreflightResult(
        verdict="REPRESENTATIVE_DEVICE_READY",
        eligible_for_physical_qualification=True,
        reason=(
            "Representative Adreno and an OpenCL loader were detected; artifact compatibility "
            "and exact runtime correctness/performance evidence are still required"
        ),
        facts=facts,
    )


def _run_adb(serial: str, args: Sequence[str]) -> str:
    command = ["adb", "-s", serial, *args]
    completed = subprocess.run(
        command,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip() or "unknown adb error"
        raise RuntimeError(f"adb command failed ({' '.join(args)}): {detail}")
    return completed.stdout.strip()


def _getprop(serial: str, name: str, runner: Callable[[str, Sequence[str]], str]) -> str:
    return runner(serial, ["shell", "getprop", name]).strip()


def _renderer(serial: str, runner: Callable[[str, Sequence[str]], str]) -> str:
    surface_flinger = runner(serial, ["shell", "dumpsys", "SurfaceFlinger"])
    for line in surface_flinger.splitlines():
        if "GLES:" in line:
            return line.split("GLES:", 1)[1].strip()
    return ""


def _opencl_library(serial: str, runner: Callable[[str, Sequence[str]], str]) -> str | None:
    quoted_paths = " ".join(OPENCL_LIBRARY_CANDIDATES)
    script = f'for p in {quoted_paths}; do if [ -f "$p" ]; then echo "$p"; break; fi; done'
    value = runner(serial, ["shell", "sh", "-c", script]).strip()
    return value or None


def collect_device_facts(
    serial: str,
    runner: Callable[[str, Sequence[str]], str] = _run_adb,
) -> DeviceFacts:
    state = runner(serial, ["get-state"]).strip()
    if state != "device":
        raise RuntimeError(f"adb device is not ready: {state or 'unknown state'}")
    return DeviceFacts(
        model=_getprop(serial, "ro.product.model", runner),
        soc=_getprop(serial, "ro.soc.model", runner),
        board_platform=_getprop(serial, "ro.board.platform", runner),
        gpu_renderer=_renderer(serial, runner),
        opencl_library_path=_opencl_library(serial, runner),
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Check whether an Android device is representative enough to start LLRT-7 "
            "OpenCL physical qualification. This does not certify model/artifact support."
        )
    )
    parser.add_argument("--device", required=True, help="ADB serial")
    args = parser.parse_args()

    try:
        result = classify_device(collect_device_facts(args.device))
    except (OSError, RuntimeError) as error:
        print(
            json.dumps(
                {
                    "verdict": "PREFLIGHT_ERROR",
                    "eligible_for_physical_qualification": False,
                    "reason": str(error),
                },
                sort_keys=True,
            )
        )
        return 2

    print(json.dumps(asdict(result), sort_keys=True))
    return 0 if result.eligible_for_physical_qualification else 3


if __name__ == "__main__":
    raise SystemExit(main())
