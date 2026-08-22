#!/usr/bin/env python3
"""Deterministic host tests for LLRT-7 OpenCL provisioning and packaging guards."""

from __future__ import annotations

import struct
import subprocess
import sys
import tempfile
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent
SDK_PREFLIGHT = ROOT / "llrt7_opencl_sdk_preflight.py"
PACKAGING = ROOT / "verify_llrt7_opencl_packaging.py"
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


def main() -> int:
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        test_sdk_preflight(root)
        test_packaging_guard(root)
    print("LLRT-7 OpenCL tool tests passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
