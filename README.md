# Android Local LLM Harness

A local-first Android harness for running explicit GGUF models through `llama.cpp`. The runtime is embedded in native Android applications today while public contracts preserve a path toward Capacitor adapters and a future shared Android service.

## Core decisions

- **Explicit model binding:** every application/use case resolves a reviewed GGUF artifact and runtime profile.
- **GGUF first:** `llama.cpp` is the initial native backend.
- **Embedded first, shared later:** in-process transport is current; backend-neutral contracts preserve future Binder deployment.
- **Privacy by default:** prompts and generated output are excluded from normal telemetry and shared reports.
- **Observability as a domain:** runs, logs, health, resources, cache state and benchmarks are first-class contracts.
- **Verified distribution:** catalog selection, download, installation, selection and runtime loading are separate explicit operations.
- **Measured performance:** residency, context, cache and device policies require representative evidence.

## Repository map

```text
core/contracts                         Public request, session, generation and error contracts
core/runtime-core                      Runtime orchestration, queueing, lifecycle and telemetry emission
models/model-profile                   GGUF profiles, use cases and application bindings
models/model-store                     Content-addressed installed-model storage and integrity
models/model-catalog                   Curated release contracts, targeting and compatibility
models/model-download                  Verified remote transfer and opaque holding-area access
models/model-install                   GGUF inspection and explicit ModelStore installation
backends/llama-cpp                     Kotlin/JNI/C++ backend and GGUF inspector adapter
observability/contracts                Runs, logs, health, resources, retention and benchmark contracts
observability/in-memory-store          Bounded ephemeral telemetry implementation
observability/room-store               Persistent Android Room telemetry implementation
observability/health-engine            Health, sanity and model-integrity execution
observability/android-resource-probe   Android memory and thermal capture
observability/benchmark-engine         Baselines, retained history and regression evaluation
transports/in-process                  Embedded LocalLlmClient delegation
ui/design-system                       Shared Compose theme and Harness components
apps/local-llm-console                 Standalone developer-console application
apps/device-test-runner                ADB/instrumentation lifecycle validation
apps/local-llm-phone-test              Connected Compose console and Play-installable validation app
```

`settings.gradle.kts` is the authoritative module list.

## Request resolution

```text
applicationId + useCaseId
          -> AppModelBinding
          -> UseCaseProfile
          -> GgufModelProfile
          -> exact GGUF digest and llama.cpp load configuration
```

The harness never silently substitutes a model. Fallbacks, when explicitly introduced, must be ordered and visible in diagnostics.

## Remote model lifecycle

```text
catalog release
  -> compatibility evaluation
  -> secure verified download
  -> opaque VerifiedDownloadHandle
  -> explicit installation and GGUF inspection
  -> ModelStore publication and verification
  -> installed metadata
  -> explicit selection/binding
  -> runtime prepare and inference
```

A verified download is not installed. Installation does not select or load a model. Selection does not imply RAM residency.

## Current integrated baseline

The promoted baseline includes:

- pinned `llama.cpp`, Android `arm64-v8a` packaging and host-native tests;
- GGUF inspection, SHA-256 import, verification, deduplication and protected removal;
- local load, context creation, generation, streaming and cooperative cancellation;
- single-decode scheduling, warm reuse and Android memory-pressure handling;
- curated catalog, secure verified download and explicit installation;
- model-aware presets, prompt/template planning, exact token planning, output constraints, stop handling and repetition protection;
- bounded in-memory and Room telemetry, request timelines, health, resources and benchmark history;
- connected Compose phone surfaces for Overview, Playground, Models, Diagnostics and Settings;
- ViewModel/UDF ownership for Playground and Models;
- typed Settings, request-timeline and model-detail routes;
- unified model inventory and deterministic runtime/selection recovery;
- reproducible Android launcher identity and shared design-system foundations.

The runtime is not production-ready until the required representative physical-device GGUF evidence is complete.

## Current priorities

1. Complete the remaining Overview, Diagnostics and Settings migration out of `MainActivity`.
2. Implement explicit RAM load/unload controls and monotonic warm-idle TTL eviction.
3. Complete navigation restoration, Compose screenshot, accessibility and responsive-layout evidence.
4. Build the signed candidate, distribute it through Google Play Internal Testing and capture representative physical-device evidence.
5. Continue native Android and Capacitor adapter work only after the embedded boundary and release gates are stable.

Current state and the ordered next block are maintained in [`docs/current-state.md`](docs/current-state.md). Capability milestones are in [`docs/roadmap.md`](docs/roadmap.md). Harness 0.5 release gates are in [`docs/releases/harness-0.5.md`](docs/releases/harness-0.5.md).

## Build prerequisites

- JDK 17
- Android SDK API 36
- Android Build Tools 36.0.0
- Android NDK 28.2.13676358
- Gradle 9.5.0 through the committed wrapper
- Android Gradle Plugin 9.3.1

## Common workflows

Build the signed release bundle or run the connected debug application by following [`docs/android-build-and-run.md`](docs/android-build-and-run.md).

```bash
# Signed release bundle for Google Play Console
bash scripts/build-phone-test-release.sh build

# Install and launch the debug phone application on a running emulator
bash scripts/run-emulator-debug.sh --app phone-test
```

The emulator runner does not create or boot an Android Virtual Device.

## Validation

Ordinary work starts from the latest green `dev` and targets `dev`. Pull requests use scoped validation; merge pushes on `dev` run cumulative validation; promotions to `main` use complete Android, native and packaging gates.

The stable required-check name is:

```text
Repository validation
```

Documentation changes additionally run:

```bash
python3 scripts/verify-docs.py
python3 scripts/verify-agent-navigation.py
```

Coding agents start from [`AGENTS.md`](AGENTS.md), which routes work to the owning module without requiring every planning document to be loaded.

## Device validation

For ADB/instrumentation execution, use [`docs/device-e2e-testing.md`](docs/device-e2e-testing.md). For Play-delivered validation without developer mode, configure the external upload key using [`docs/android-upload-key.md`](docs/android-upload-key.md) and follow [`docs/play-internal-phone-test.md`](docs/play-internal-phone-test.md).

Signing keys, credentials and GGUF files must never be committed.

## Documentation

The documentation ownership map is [`docs/README.md`](docs/README.md). Durable architecture is in [`docs/architecture.md`](docs/architecture.md) and accepted ADRs. Target behavior is in [`docs/implementation-plan.md`](docs/implementation-plan.md) and focused feature specifications. Completion rules are in [`docs/definition-of-done.md`](docs/definition-of-done.md).
