# llama.cpp Backend — Coding Agent Guide

## Scope

This guide applies to `backends/llama-cpp/**` and supplements the repository-wide [`AGENTS.md`](../../AGENTS.md). It covers the Kotlin backend adapter, JNI entry points, C++ ownership, native build configuration, GGUF inspection adaptation and host-native tests.

The pinned source under `third_party/llama.cpp` has its own upstream guide. Treat it as third-party source; integration policy and local patches belong here or in repository documentation.

## Navigation

Read these sources according to the change:

| Concern | Start here | Supporting source |
| --- | --- | --- |
| Kotlin/backend contract adaptation | `src/main/kotlin` | [`api-usage.md`](../../docs/api-usage.md), [`architecture.md`](../../docs/architecture.md) |
| JNI entry points, UTF conversion and output buffering | `src/main/cpp/llama_jni_entry.cpp`, `llama_jni.cpp`, `generation_output_buffer.*` | [`definition-of-done.md`](../../docs/definition-of-done.md), native tests under `src/test-native` |
| Native model/context/cancellation ownership | Registry headers and generation implementation | [`architecture.md`](../../docs/architecture.md), native tests under `src/test-native` |
| GGUF metadata inspection | `gguf_metadata.*` and Kotlin inspector adapter | [`model-installation.md`](../../docs/model-installation.md), [ADR 0007](../../docs/adr/0007-explicit-verified-download-installation.md) |
| CMake, ABI or packaged libraries | `src/main/cpp/CMakeLists.txt`, module Gradle file | [`device-e2e-testing.md`](../../docs/device-e2e-testing.md), packaging verification script |
| Upstream revision | `third_party/llama.cpp` pin and verification scripts | Repository root guide, upstream release notes and affected benchmarks |

Trace both sides of a native call before editing it:

```bash
rg '<jni-method-or-handle>' backends/llama-cpp core/runtime-core apps/device-test-runner
rg --files backends/llama-cpp/src/main backends/llama-cpp/src/test backends/llama-cpp/src/test-native
```

## Local invariants

- Native pointers and backend-owned structures never cross the backend boundary; expose opaque IDs or neutral results.
- Model, context and cancellation registries own their resources explicitly, reject invalid handles and release idempotently.
- Partial initialization, generation failure, cancellation and shutdown release every resource already acquired and leave the runtime recoverable.
- Keep one native streaming decode path for prompt preparation, tokenization, sampling, prefill,
  decode, stop handling, metrics and cleanup. Aggregate above the native boundary when needed.
- Keep JNI coarse-grained and keep UTF-8 chunk buffering and stop matching in testable native helpers.
- Backend errors map to typed, privacy-safe contracts. Do not make product behavior depend on free-form native messages.
- GGUF inspection used by installation remains metadata-only and adapts to the neutral `model-install` contract.
- Native implementation files are compiled and linked normally; do not include one `.cpp` file from another.
- CPU `arm64-v8a` remains the supported release baseline. Do not infer device support from host or emulator success.
- Do not commit generated native build directories, model binaries, local patches hidden inside the submodule or machine-specific paths.

## Change routing

- Put public/backend-independent contract changes in `core/contracts` or the owning neutral module before adapting them here.
- Put runtime scheduling, model-switch and lifecycle policy in `core/runtime-core`; this module implements backend operations and resource ownership.
- Put installation sequencing in `models/model-install`; this module only supplies the GGUF inspector adapter.
- Add pure C++ behavior to a focused source/header pair with host-native coverage.
- Add Kotlin bridge behavior with deterministic JVM tests for success, error, cancellation and close semantics.
- Treat an upstream pin update as a compatibility change: inspect API deltas, build flags, GGUF support, packaging and benchmark impact.

## Validation

Run the backend Kotlin gate and native host suite:

```bash
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew :backends:llama-cpp:compileDebugKotlin \
  :backends:llama-cpp:compileDebugUnitTestKotlin \
  :backends:llama-cpp:testDebugUnitTest \
  :backends:llama-cpp:lintDebug
cmake -S backends/llama-cpp/src/test-native -B build/native-tests -DCMAKE_BUILD_TYPE=Release
cmake --build build/native-tests --parallel 2
ctest --test-dir build/native-tests --output-on-failure
```

For JNI, CMake, ABI, upstream-pin or native-library changes, also run:

```bash
./gradlew :backends:llama-cpp:assembleDebug \
  :apps:device-test-runner:assembleDebug \
  :apps:device-test-runner:assembleDebugAndroidTest \
  :apps:local-llm-phone-test:assembleDebug
bash scripts/verify-llama-cpp-pin.sh
python3 scripts/verify-android-packaging.py
```

Use the repository-wide gate for shared contracts or multi-module changes. Real GGUF generation, cancellation, lifecycle, PSS and thermal claims require the physical-device evidence procedure; emulator evidence is preflight only.

## Maintaining this guide

Update this file in the same change when:

- JNI entry points, registries, native ownership or source decomposition change;
- CMake targets, packaged libraries, ABI policy or Gradle native configuration change;
- the upstream pin/update procedure or local patch policy changes;
- GGUF inspection responsibilities or neutral adapter contracts move;
- Kotlin or host-native test commands change;
- new direct runtime, installer or device-test consumers appear.

Update the root map only when the backend's repository-level responsibility changes. Update architecture and an ADR when native ownership, public boundaries or backend selection policy changes. Update device docs when the execution or evidence procedure changes, and roadmap/current state only when evidence status changes.

After editing, run from the repository root:

```bash
python3 scripts/verify-agent-navigation.py
```
