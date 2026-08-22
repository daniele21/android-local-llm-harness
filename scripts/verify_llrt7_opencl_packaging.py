#!/usr/bin/env python3
"""Verify the experimental LLRT-7 OpenCL payload without redistributing a loader."""

from __future__ import annotations

import argparse
import struct
import sys
from pathlib import Path
from zipfile import BadZipFile, ZipFile

EXPECTED_ABI = "arm64-v8a"
OPENCL_BACKEND = "libggml-opencl.so"
OPENCL_LOADER = "libOpenCL.so"
EM_AARCH64 = 183
ELF_HEADER_BYTES = 20


class PackagingError(RuntimeError):
    """Raised when an experimental OpenCL archive is not safe to execute."""


def verify_aarch64_elf(archive: ZipFile, entry: str) -> None:
    with archive.open(entry) as library:
        header = library.read(ELF_HEADER_BYTES)
    if len(header) < ELF_HEADER_BYTES or header[:4] != b"\x7fELF":
        raise PackagingError(f"{entry} is not an ELF shared library")
    if header[4] != 2:
        raise PackagingError(f"{entry} is not a 64-bit ELF library")
    byte_order = "<" if header[5] == 1 else ">" if header[5] == 2 else None
    if byte_order is None:
        raise PackagingError(f"{entry} has an unsupported ELF byte order")
    machine = struct.unpack_from(f"{byte_order}H", header, 18)[0]
    if machine != EM_AARCH64:
        raise PackagingError(f"{entry} targets ELF machine {machine}, expected AArch64")


def verify_archive(path: Path, prefix: str, label: str) -> None:
    if not path.is_file():
        raise PackagingError(f"{label} does not exist: {path}")
    backend_entry = f"{prefix}{EXPECTED_ABI}/{OPENCL_BACKEND}"
    loader_entry = f"{prefix}{EXPECTED_ABI}/{OPENCL_LOADER}"
    try:
        with ZipFile(path) as archive:
            entries = set(archive.namelist())
            if backend_entry not in entries:
                raise PackagingError(f"{label} is missing {backend_entry}")
            if loader_entry in entries:
                raise PackagingError(
                    f"{label} unexpectedly redistributes {OPENCL_LOADER}; "
                    "the experimental lane must use the device-provided loader"
                )
            verify_aarch64_elf(archive, backend_entry)
    except BadZipFile as error:
        raise PackagingError(f"{label} is not a valid ZIP archive: {path}") from error

    print(f"Verified {label}: {path}")
    print(f"  backend: {backend_entry}")
    print(f"  packaged OpenCL loader: no")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True, help="Experimental device-test application APK")
    parser.add_argument("--aar", type=Path, required=True, help="Experimental llama.cpp backend AAR")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    verify_archive(args.apk, "lib/", "device-test OpenCL APK")
    verify_archive(args.aar, "jni/", "llama.cpp OpenCL AAR")
    print("LLRT-7 OpenCL packaging verification completed successfully")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PackagingError as error:
        print(f"LLRT-7 OpenCL packaging verification failed: {error}", file=sys.stderr)
        raise SystemExit(1) from error
