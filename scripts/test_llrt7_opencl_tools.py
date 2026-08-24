#!/usr/bin/env python3
"""Deterministic host tests for LLRT-7 OpenCL provisioning and packaging guards."""

from __future__ import annotations

import struct
import subprocess
import sys
import tempfile
from pathlib import Path
from zipfile import ZipFile

from llrt7_opencl_device_preflight import OPENCL_LIBRARY_CANDIDATES, _opencl_library

ROOT = Path(__file__).resolve().parent
SDK_PREFLIGHT = ROOT / "llrt7_opencl_sdk_preflight.py"
PACKAGING = ROOT / "verify_llrt7_opencl_packaging.py"
QUICK_RUNNER = ROOT / "run-llrt-quick-physical-evidence.sh"
EM_AARCH64 = 183
EM_X86_64 = 62


def elf_header(machine: int) -> bytes:
    header = bytearray(20)
    header[:4] = b"\x7fELF"
    header[4] = 2
    header[5] = 1
    struct.pack_into("<H", header, 18, machine)
    return bytes(header)


def run(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, *args],
        check=False,
        capture_output=True,
        text=True,
    )


def create_headers(root: Path) -> Path:
    include = root / "include"
    cl = include / "CL"
    cl.mkdir(parents=True)
    (cl / "cl.h").write_text("/* synthetic OpenCL header */\n", encoding="utf-8")
    (cl / "cl_platform.h").write_text("/* synthetic platform header */\n", encoding="utf-8")
    return include


def create_archive(path: Path, prefix: str, include_loader: bool = False) -> None:
    with ZipFile(path, "w") as archive:
        archive.writestr(f"{prefix}arm64-v8a/libggml-opencl.so", elf_header(EM_AARCH64))
        if include_loader:
            archive.writestr(f"{prefix}arm64-v8a/libOpenCL.so", elf_header(EM_AARCH64))


def test_sdk_preflight(root: Path) -> None:
    include = create_headers(root)
    arm_loader = root / "libOpenCL-arm64.so"
    arm_loader.write_bytes(elf_header(EM_AARCH64))
    result = run(str(SDK_PREFLIGHT), "--include-dir", str(include), "--library", str(arm_loader))
    assert result.returncode == 0, result.stderr
    assert "OpenCL SDK preflight passed" in result.stdout

    host_loader = root / "libOpenCL-host.so"
    host_loader.write_bytes(elf_header(EM_X86_64))
    result = run(str(SDK_PREFLIGHT), "--include-dir", str(include), "--library", str(host_loader))
    assert result.returncode != 0
    assert "expected AArch64" in result.stderr


def test_packaging_guard(root: Path) -> None:
    apk = root / "opencl.apk"
    aar = root / "opencl.aar"
    create_archive(apk, "lib/")
    create_archive(aar, "jni/")
    result = run(str(PACKAGING), "--apk", str(apk), "--aar", str(aar))
    assert result.returncode == 0, result.stderr
    assert "packaged OpenCL loader: no" in result.stdout

    unsafe_apk = root / "unsafe.apk"
    create_archive(unsafe_apk, "lib/", include_loader=True)
    result = run(str(PACKAGING), "--apk", str(unsafe_apk), "--aar", str(aar))
    assert result.returncode != 0
    assert "unexpectedly redistributes libOpenCL.so" in result.stderr


def test_device_loader_probe_uses_direct_adb_shell() -> None:
    calls: list[tuple[str, list[str]]] = []
    expected = OPENCL_LIBRARY_CANDIDATES[0]

    def fake_runner(serial: str, args: list[str]) -> str:
        calls.append((serial, list(args)))
        return expected

    actual = _opencl_library("device-serial", fake_runner)
    assert actual == expected
    assert len(calls) == 1
    serial, args = calls[0]
    assert serial == "device-serial"
    assert args[0] == "shell"
    assert len(args) == 2, f"nested shell invocation is unsafe through adb: {args}"
    assert args[1].startswith("for p in ")
    for candidate in OPENCL_LIBRARY_CANDIDATES:
        assert candidate in args[1]


def test_quick_runner_avoids_empty_optional_array_under_nounset() -> None:
    source = QUICK_RUNNER.read_text(encoding="utf-8")
    assert '"${RESET[@]}"' not in source
    assert "run_with_optional_reset()" in source
    assert source.count("run_with_optional_reset bash") == 3


def main() -> int:
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        test_sdk_preflight(root)
        test_packaging_guard(root)
    test_device_loader_probe_uses_direct_adb_shell()
    test_quick_runner_avoids_empty_optional_array_under_nounset()
    print("LLRT-7 OpenCL tool tests passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
