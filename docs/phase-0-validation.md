# Phase 0 validation

This document records the final repository-hardening validation gate.

## Toolchain decision

Android API 36 is used as the stable compile and target SDK. API 37 remains a preview upgrade and is deferred until its platform package is consistently available to CI runners.

## Required checks

- [x] Gradle Wrapper generated and committed.
- [ ] Formatting checks pass.
- [ ] Detekt CLI analysis passes.
- [ ] Android Lint passes for debug and internal variants.
- [ ] Unit tests pass.
- [ ] Debug Android libraries and console APK build successfully.
- [ ] Internal console APK builds successfully.
- [ ] No GGUF or GGML binaries are present in the source tree.
- [ ] CI publishes APK, AAR and report artifacts.

The checklist is updated only from observed CI results.
