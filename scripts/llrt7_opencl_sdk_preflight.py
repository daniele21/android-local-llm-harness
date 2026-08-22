#!/usr/bin/env python3
"""Validate explicit host-side inputs for the LLRT-7 Android OpenCL build lane."""

from __future__ import annotations

import argparse
import struct
import sys
from pathlib import Path

EM_AARCH64 = 183
ELF_HEADER_BYTES = 20


class PreflightError(RuntimeError):
    """Raised when the supplied OpenCL build inputs are not Android arm64 inputs."""


def verify_loader(path: Path) -> None:
    if not path.is_file() or not path.stat().st_size:
        raise PreflightError(f"OpenCL loader is missing or empty: {path}")
    header = path.read_bytes()[:ELF_HEADER_BYTES]
    if len(header) < ELF_HEADER_BYTES or header[:4] != b"\x7fELF":
        raise PreflightError(f"OpenCL loader is not ELF: {path}")
    if header[4] != 2:
        raise PreflightError("OpenCL loader must be 64-bit")
    byte_order = "<" if header[5] == 1 else ">" if header[5] == 2 else None
    if byte_order is None:
        raise PreflightError("OpenCL loader has unsupported ELF byte order")
    machine = struct.unpack_from(f"{byte_order}H", header, 18)[0]
    if machine != EM_AARCH64:
        raise PreflightError(f"OpenCL loader targets ELF machine {machine}, expected AArch64")


def verify_headers(path: Path) -> None:
    if not path.is_dir():
        raise PreflightError(f"OpenCL include directory is missing: {path}")
    required = (path / "CL" / "cl.h", path / "CL" / "cl_platform.h")
    missing = [str(header) for header in required if not header.is_file()]
    if missing:
        raise PreflightError(f"OpenCL headers are incomplete; missing={missing}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--include-dir", type=Path, required=True)
    parser.add_argument("--library", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    verify_headers(args.include_dir)
    verify_loader(args.library)
    print("LLRT-7 OpenCL SDK preflight passed")
    print(f"  include dir: {args.include_dir.resolve()}")
    print(f"  loader: {args.library.resolve()}")
    print("  loader ELF: AArch64")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PreflightError as error:
        print(f"LLRT-7 OpenCL SDK preflight failed: {error}", file=sys.stderr)
        raise SystemExit(1) from error
