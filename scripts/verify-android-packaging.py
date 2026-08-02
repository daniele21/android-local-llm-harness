#!/usr/bin/env python3
"""Validate native Android packaging produced by the Phase 1 build."""

from __future__ import annotations

import struct
import sys
from pathlib import Path
from zipfile import BadZipFile, ZipFile

EXPECTED_LIBRARIES = {
    "libc++_shared.so",
    "libggml-base.so",
    "libggml-cpu-android_armv8.0_1.so",
    "libggml-cpu-android_armv8.2_1.so",
    "libggml-cpu-android_armv8.2_2.so",
    "libggml-cpu-android_armv8.6_1.so",
    "libggml-cpu-android_armv9.0_1.so",
    "libggml-cpu-android_armv9.2_1.so",
    "libggml-cpu-android_armv9.2_2.so",
    "libggml.so",
    "libllama.so",
    "liblocal_llm_jni.so",
}
EXPECTED_ABI = "arm64-v8a"
EM_AARCH64 = 183
ELF_HEADER_BYTES = 20


class PackagingError(RuntimeError):
    """Raised when an Android archive does not match the expected layout."""


def find_single(root: Path, pattern: str, label: str) -> Path:
    matches = sorted(root.glob(pattern))
    if len(matches) != 1:
        rendered = ", ".join(str(path) for path in matches) or "none"
        raise PackagingError(f"Expected one {label}; found {len(matches)}: {rendered}")
    return matches[0]


def native_entries(archive: ZipFile, prefix: str) -> list[str]:
    return sorted(
        name
        for name in archive.namelist()
        if name.startswith(prefix) and name.endswith(".so")
    )


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


def verify_native_archive(path: Path, prefix: str, label: str) -> None:
    try:
        with ZipFile(path) as archive:
            entries = native_entries(archive, prefix)
            if not entries:
                raise PackagingError(f"{label} contains no native libraries")

            abi_values = {entry.split("/")[1] for entry in entries}
            if abi_values != {EXPECTED_ABI}:
                raise PackagingError(
                    f"{label} contains unexpected ABIs: {sorted(abi_values)}"
                )

            library_names = [Path(entry).name for entry in entries]
            if len(library_names) != len(set(library_names)):
                raise PackagingError(f"{label} contains duplicate native library names")

            actual_libraries = set(library_names)
            missing = sorted(EXPECTED_LIBRARIES - actual_libraries)
            unexpected = sorted(actual_libraries - EXPECTED_LIBRARIES)
            if missing or unexpected:
                raise PackagingError(
                    f"{label} native library mismatch; missing={missing}, unexpected={unexpected}"
                )

            for entry in entries:
                verify_aarch64_elf(archive, entry)
    except BadZipFile as error:
        raise PackagingError(f"{label} is not a valid ZIP archive: {path}") from error

    print(f"Verified {label}: {path}")
    print(f"  ABI: {EXPECTED_ABI}")
    print(f"  native libraries: {len(EXPECTED_LIBRARIES)}")


def verify_instrumentation_apk(path: Path) -> None:
    try:
        with ZipFile(path) as archive:
            entries = native_entries(archive, "lib/")
    except BadZipFile as error:
        raise PackagingError(f"Instrumentation APK is not a valid ZIP archive: {path}") from error
    if entries:
        raise PackagingError(
            "Instrumentation APK unexpectedly packages native libraries: "
            + ", ".join(entries)
        )
    print(f"Verified instrumentation APK has no duplicate native payload: {path}")


def main() -> int:
    repository = Path(__file__).resolve().parents[1]
    application_apk = find_single(
        repository,
        "apps/device-test-runner/build/outputs/apk/debug/*-debug.apk",
        "device-test application APK",
    )
    instrumentation_apk = find_single(
        repository,
        "apps/device-test-runner/build/outputs/apk/androidTest/debug/*-androidTest.apk",
        "device-test instrumentation APK",
    )
    backend_aar = find_single(
        repository,
        "backends/llama-cpp/build/outputs/aar/llama-cpp-debug.aar",
        "llama.cpp debug AAR",
    )

    verify_native_archive(application_apk, "lib/", "device-test application APK")
    verify_instrumentation_apk(instrumentation_apk)
    verify_native_archive(backend_aar, "jni/", "llama.cpp debug AAR")
    print("Android native packaging verification completed successfully")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PackagingError as error:
        print(f"Android packaging verification failed: {error}", file=sys.stderr)
        raise SystemExit(1) from error
