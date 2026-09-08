# Changelog

All notable released changes to Harnex are documented here.

Published SDK artifacts follow [Semantic Versioning](https://semver.org/). Host application, Consumer SDK, Binder protocol and model/runtime identities are versioned independently; see [`docs/versioning.md`](docs/versioning.md).

## [Unreleased]

Harnex `0.5.0` is the current pre-release integration target. It is not production-ready until the applicable release and physical-device evidence gates are complete.

### Added

- Android local-AI control plane and shared runtime architecture.
- Curated local GGUF model installation, integrity verification, selection and runtime residency lifecycle.
- Pinned `llama.cpp` Android backend behind a backend-neutral runtime SPI.
- Streaming local generation, cooperative cancellation, explicit sessions and recoverable runtime lifecycle.
- Versioned Android Consumer SDK and Binder transport for external applications.
- Runnable standalone `samples/hello-harnex` onboarding app, compiled against the published Consumer SDK rather than repository-internal modules.
- Android-identity-based Consumer authorization using Binder UID, installed package, signing identity, Harnex authorization and enabled use case.
- Reversible Consumer `connect()` / `disconnect()` plus terminal `close()` lifecycle.
- Durable logical inference jobs that can be queried after transient Binder reconnects without duplicate submission.
- Connected Harnex Android product surfaces: Overview, Playground, Activity, Applications, Performance, Models, Diagnostics and Settings.
- Privacy-safe runtime telemetry, health, benchmark, memory and thermal evidence.
- Bounded encrypted local inference Activity/audit separated from normal telemetry and logs.
- Automated repository architecture, Consumer SDK, Binder, lifecycle, UI and evidence validation.
- Explicit physical-device evidence contracts for claims that cannot be established by emulator/host CI.

### Changed

- Project identity is now **Harnex — Your local AI harness for Android**.
- `samples/hello-harnex` now serves as the golden Consumer-app ownership reference: transient UI is separated from lifecycle-owned state, the public SDK sits behind a small app-owned boundary, terminal cleanup is race-hardened and focused lifecycle/failure tests run against publication artifacts.
- Product/runtime model support is curated around reviewed Qwen3.5 GGUF artifacts rather than arbitrary model-family claims.
- Runtime policy, model lifecycle and observability are host-owned rather than duplicated in Consumer applications.
- Repository governance follows the `repo-template-sw` engineering model while preserving Harnex-specific Android/local-AI constraints.

### Current public development artifact

- Consumer Android SDK: `io.github.daniele21.localllm:consumer-android:0.1.0-alpha.11`.

## Release history

The first tagged Harnex release will establish the normal dated release sections below this line. Until then, pre-release implementation history remains available through Git history and the active [`docs/releases/harness-0.5.md`](docs/releases/harness-0.5.md) release checklist.
