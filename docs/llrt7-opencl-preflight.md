# LLRT-7 OpenCL preflight

Status: experimental
Document type: implementation-note
Owner: llama-cpp-runtime
Canonical scope: target.llama-cpp-runtime-opencl-preflight
Read when: enabling or validating the opt-in Android OpenCL build lane
Last reviewed: 2026-08-21

## Boundary

The Android release/default build remains CPU-only. The experimental lane is enabled only with the Gradle property `localLlm.experimentalOpenCl=true`, which maps to `LOCAL_LLM_EXPERIMENTAL_OPENCL=ON` in the llama.cpp CMake integration.

When enabled, the pinned upstream build is asked to emit the dynamic `ggml-opencl` backend with embedded kernels and Adreno-oriented kernels. The existing backend loader already scans the application native-library directory and the existing device inventory reports registered ggml devices; this slice therefore does not add a second loader or product-level backend-selection policy.

## External prerequisites

Android NDK builds do not provide the OpenCL development inputs used by upstream `find_package(OpenCL)` by default. An experimental build must provide compatible OpenCL headers and a linkable OpenCL loader/ICD implementation to CMake. Missing prerequisites are a build-time failure for the opt-in lane and must never weaken the default CPU build.

The pinned upstream OpenCL documentation verifies Android primarily on Qualcomm Adreno 750/830 class devices and warns that older A6xx phone drivers can be unsupported. Physical performance/support claims therefore require a representative Adreno device; CPU-only or non-Adreno phone evidence does not certify this lane.

The current curated Qwen3.5 artifacts are Q4_K_M. Upstream documentation at the pinned revision does not make Q4_K a production-ready optimized OpenCL claim, so model compatibility, correctness and quality must be demonstrated before any OpenCL default or performance claim.

## Validation

The repository-level preflight guard is:

```bash
python3 scripts/verify-llama-cpp-opencl-preflight.py
```

A future experimental build/evidence lane must additionally verify that `libggml-opencl.so` is packaged, is dynamically discovered, reports the expected device identity and runs exact-artifact correctness/performance evidence. None of those results are inferred from the build toggle alone.
