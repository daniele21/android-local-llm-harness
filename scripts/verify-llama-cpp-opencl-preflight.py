#!/usr/bin/env python3
"""Verify the LLRT-7 OpenCL build lane stays explicit and release-default-off."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
CMAKE = ROOT / "backends/llama-cpp/src/main/cpp/CMakeLists.txt"
GRADLE = ROOT / "backends/llama-cpp/build.gradle.kts"

cmake = CMAKE.read_text(encoding="utf-8")
gradle = GRADLE.read_text(encoding="utf-8")

required_cmake = (
    'option(LOCAL_LLM_EXPERIMENTAL_OPENCL "Build the experimental ggml OpenCL backend" OFF)',
    'set(GGML_OPENCL ON CACHE BOOL "" FORCE)',
    'set(GGML_OPENCL OFF CACHE BOOL "" FORCE)',
    'set(GGML_OPENCL_EMBED_KERNELS ON CACHE BOOL "" FORCE)',
    'set(GGML_OPENCL_USE_ADRENO_KERNELS ON CACHE BOOL "" FORCE)',
)
required_gradle = (
    'gradleProperty("localLlm.experimentalOpenCl")',
    '-DLOCAL_LLM_EXPERIMENTAL_OPENCL=',
)

missing = [entry for entry in required_cmake if entry not in cmake]
missing += [entry for entry in required_gradle if entry not in gradle]

if missing:
    for entry in missing:
        print(f"missing OpenCL preflight contract: {entry}", file=sys.stderr)
    raise SystemExit(1)

print("LLRT-7 OpenCL build contract is explicit and default-off")
