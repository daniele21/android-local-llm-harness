# Phase 0 validation

This document records the final repository-hardening validation gate.

## Toolchain decision

Android API 36 is used as the stable compile and target SDK. API 37 remains a preview upgrade and is deferred until its platform package is consistently available to CI runners.

## Validated configuration

- Android Gradle Plugin 9.3.0
- Gradle Wrapper 9.5.0 with distribution checksum
- JDK 17
- Android API 36
- Android Build Tools 36.0.0
- Android NDK 28.2.13676358
- arm64-v8a native backend stub

## Required checks

- [x] Gradle Wrapper generated, validated and committed.
- [x] Spotless and ktlint formatting checks pass.
- [x] Detekt CLI analysis passes.
- [x] Android Lint passes for debug and internal variants.
- [x] Unit tests pass.
- [x] Debug Android libraries and console APK build successfully.
- [x] Internal console APK builds successfully.
- [x] Native CMake/JNI stub compiles for arm64-v8a.
- [x] No GGUF or GGML binaries are present in the source tree.
- [x] CI publishes APK, AAR and validation-report artifacts.

## Evidence

GitHub Actions run `30719080856` completed successfully on 2026-08-01.

Published artifacts:

- `local-llm-console-apks`
- `local-llm-harness-aars`
- `validation-reports`

The CI gate executes formatting, static analysis, unit tests, Android Lint, debug/internal builds, native CMake compilation and the model-binary guard from a clean checkout.
