# Changelog

All notable released changes to Harnex are documented here.

Published SDK artifacts follow [Semantic Versioning](https://semver.org/). Host application, Consumer SDK, Binder protocol and model/runtime identities are versioned independently; see [`docs/versioning.md`](docs/versioning.md).

## [Unreleased]

Future changes after the first Harnex GitHub prerelease are collected here.

## [0.5.0-rc.1]

Harnex `0.5.0-rc.1` is the first public GitHub prerelease target. It remains pre-stable and must not be described as universally production-ready beyond the release evidence attached to the exact candidate.

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
- Official direct GitHub APK distribution alongside optional Google Play distribution, with independent signing identities and explicit channel-switch semantics.
- Immutable GitHub release-candidate preparation, SHA-256 release manifest/checksums, build provenance attestation and publish-without-rebuild workflow.

### Changed

- Project identity is now **Harnex — Your local AI harness for Android**.
- `samples/hello-harnex` now serves as the golden Consumer-app ownership reference: transient UI is separated from lifecycle-owned state, the public SDK sits behind a small app-owned boundary, terminal cleanup is race-hardened and focused lifecycle/failure tests run against publication artifacts.
- Product/runtime model support is curated around reviewed Qwen3.5 GGUF artifacts rather than arbitrary model-family claims.
- Runtime policy, model lifecycle and observability are host-owned rather than duplicated in Consumer applications.
- Repository governance follows the `repo-template-sw` engineering model while preserving Harnex-specific Android/local-AI constraints.
- GitHub Releases are the primary direct-download release surface; Google Play is optional and retains its independent App Signing identity.

### Distribution and compatibility

- GitHub and Google Play use the same Host package/service contract but may use different Android signing lineages.
- Cross-channel in-place updates are not promised. Switching between GitHub and Play may require uninstall/reinstall and can remove Harnex app-private state.
- Consumer authorization remains Binder/Control-Plane-owned; sharing or differing Host distribution signers does not grant Consumer authority.

### Known limitations

- This is a prerelease. Release notes and evidence must distinguish deterministic emulator/CI proof from representative physical ARM64/JNI/GGUF/memory/thermal evidence.
- The reviewed Qwen3.5 4B 4-bit tier remains candidate-only until its exact-artifact representative-device runtime, memory, thermal and output-quality gates close.
- Device/OEM compatibility is curated and evidence-bound; Harnex does not claim support for every GGUF/model/device combination.

### Current public development artifact

- Consumer Android SDK: `io.github.daniele21.localllm:consumer-android:0.1.0-alpha.11`.

## Release history

`0.5.0-rc.1` is the first GitHub Release target. Until it is actually published, the Git tag and GitHub Release must not be claimed as existing; preparation and blocking release evidence remain tracked in [`docs/releases/harness-0.5.md`](docs/releases/harness-0.5.md).
