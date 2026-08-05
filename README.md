# Android Local LLM Harness

A local-first Android harness for running explicit GGUF models through `llama.cpp`, embedding the runtime inside native or Capacitor applications today and preserving a clean path toward a shared Android service later.

## Core decisions

- **Model-aware, not model-selecting:** every application/use case binds to an explicit GGUF artifact and runtime profile.
- **GGUF first:** `llama.cpp` is the initial native backend.
- **Embedded first:** applications call the same runtime through an in-process transport.
- **Shared later:** transport boundaries and serializable contracts are kept separate so Binder can replace the in-process transport.
- **Observability first:** runs, timings, health, sanity checks, cache state and structured logs are first-class domains.
- **Privacy by default:** telemetry stores metadata only; prompts and outputs are not persisted by default.
- **Measured performance:** memory, cache and execution policies must be selected from device evidence rather than assumptions.
- **Verified distribution:** remote catalog selection, verified transfer, installation, binding and runtime loading remain explicit separate operations.

## Modules

```text
core/contracts                         Stable request, session and runtime contracts
core/runtime-core                      Runtime orchestration, scheduling, lifecycle and telemetry emission
models/model-profile                   GGUF artifacts, load profiles and app bindings
models/model-store                     Content-addressed installed-model storage and integrity
models/model-catalog                   Administrator-managed catalog contracts, persistence and compatibility
models/model-download                  Verified remote transfer and opaque holding-area access
models/model-install                   GGUF inspection and explicit ModelStore installation
backends/llama-cpp                     Kotlin/JNI/C++ backend and installation inspector adapter
observability/contracts                Metrics, logs, health, retention and dashboard contracts
observability/in-memory-store          Bounded ephemeral telemetry and deterministic test implementation
observability/room-store               Persistent Android Room telemetry repository
observability/health-engine            Health orchestration, model integrity, sanity and cache checks
observability/android-resource-probe   Android memory and thermal snapshot provider
observability/benchmark-engine         Baseline capture and performance regression checks
transports/in-process                  Embedded transport implementation
ui/design-system                       Shared Compose theme and reusable Harness components
apps/local-llm-console                 Standalone developer-console application
apps/device-test-runner                ADB/instrumentation GGUF lifecycle test application
apps/local-llm-phone-test              Connected Compose console and Play-installable validation app
```

## Request resolution

```text
applicationId + useCaseId
          -> AppModelBinding
          -> UseCaseProfile
          -> GgufModelProfile
          -> exact GGUF digest and llama.cpp load configuration
```

The harness never silently substitutes another model. Declared fallbacks, when added, must remain explicit and observable.

## Remote model lifecycle

```text
catalog release selection
          -> compatibility evaluation
          -> secure verified download
          -> VerifiedDownloadHandle
          -> explicit verified installation
          -> GGUF inspection
          -> ModelStore publication and verification
          -> installed-model metadata
          -> explicit app/use-case binding
          -> runtime prepare and inference
```

A verified download is not yet an installed or active model. Installation does not activate a binding, load the runtime or start inference as a side effect.

## Build prerequisites

- JDK 17
- Android SDK API 36
- Android Build Tools 36.0.0
- Android NDK 28.2.13676358
- Gradle 9.5.0 through the committed wrapper

The repository uses Android Gradle Plugin 9.3.1 and its built-in Kotlin support. Android API 36 is the stable reproducible build target; API 37 remains a preview platform and will be adopted only when the required SDK package is consistently available in CI.

## Current state

`main` is the canonical integrated implementation line. Repository-state alignment, Android Gradle Plugin 9.3.1 and `actions/checkout@v7` were integrated through PRs #44, #45 and #47.

### Functional embedded runtime

- pinned `llama.cpp` submodule and Android `arm64-v8a` build;
- GGUF metadata inspection;
- SHA-256 content-addressed model import and verification;
- opaque model and context lifecycle;
- deterministic generation, aggregated streaming and cooperative cancellation;
- single-decode scheduling with priorities and queue cancellation;
- runtime orchestration, model reuse and model-switch protection;
- Android background and memory-pressure handling;
- Kotlin, native, simulated-acceptance, packaging and ARM64 emulator validation.

### Observability and controls

- bounded in-memory and Room-backed telemetry;
- persistent generation states, request-correlated logs and request timelines;
- queue, model-load, TTFT, prefill, decode, total, token and throughput metrics;
- explicit `COLD`, `WARM` and `UNKNOWN` load classification;
- Android memory and thermal snapshots;
- model-integrity, generation-sanity and cache-health checks;
- benchmark baselines and regression evaluation;
- cache diagnosis and targeted repair;
- privacy boundaries that exclude prompts and generated output from normal telemetry.

### Connected Android console

`apps/local-llm-phone-test` contains the connected Compose-based Harness surface with:

- Overview, Playground, Models, Diagnostics and Settings destinations;
- one process-scoped runtime graph shared by inference and physical validation;
- real GGUF import, verification, streaming inference, cancellation and removal;
- connected run, health, resource, benchmark, log and request-timeline views;
- a Play-installable physical-device validation path without developer mode or ADB.

The canonical UX/UI plan is not complete. ViewModel/UDF migration, full Navigation Compose detail routes, durable multi-model presentation, UI/screenshot/accessibility testing and representative physical-device evidence remain open.

### Administrator-managed model distribution

The catalog, downloader and installation boundaries provide:

- strict catalog contracts and fail-closed validation;
- exact application/use-case filtering and device compatibility evaluation;
- atomic app-private catalog persistence with revision, rollback and expiry handling;
- HTTPS-only, allowlisted and redirect-bounded transfer;
- DNS and address-class preflight;
- size, storage-headroom and SHA-256 verification while streaming;
- cancellation, bounded retry, restart cleanup and digest-based deduplication;
- durable publication into an app-private verified holding area;
- opaque access that never exposes the verified backing path;
- exact catalog/profile/target reconciliation before installation;
- metadata-only GGUF inspection before final publication;
- import and post-import verification through the existing `ModelStore`;
- non-destructive failure when post-import verification is invalid or unavailable;
- explicit retention or discard of verified bytes only after success;
- no implicit binding, runtime load or inference.

See [`docs/model-installation.md`](docs/model-installation.md) and ADR 0007 for the installation lifecycle.

## Current priorities

1. Integrate catalog selection, download progress, explicit installation and installed-model state into the connected phone application.
2. Add durable installed-model catalog/profile metadata without making installation activate a binding.
3. Recover only the still-unique benchmark-history and model-management behavior from legacy draft PRs #33 and #34 on fresh branches from current `main`.
4. Complete the physical-device production-readiness evidence on representative Android `arm64-v8a` hardware.
5. Complete the remaining Compose architecture, accessibility, responsive and UI-test work.
6. Add native Android and Capacitor integration surfaces as thin adapters.
7. Add a signature-protected diagnostics bridge and Binder transport before promoting the console into a shared runtime host.

See [`docs/current-state.md`](docs/current-state.md) for the active integration and recovery ledger and [`docs/roadmap.md`](docs/roadmap.md) for the detailed historical roadmap.

## Coding-agent navigation

Coding agents must start from [`AGENTS.md`](AGENTS.md). It maps modules to responsibilities, defines architectural invariants, routes common change types and lists required validation commands.

The repository validates that configured Gradle modules remain discoverable from `AGENTS.md` and that local links in agent guides resolve correctly:

```bash
python3 scripts/verify-agent-navigation.py
```

## Validation

The complete repository gate includes repository guards, scoped Android validation, native host tests and packaging verification. The stable aggregate required-check name is:

```text
Repository validation
```

The dedicated model-distribution workflow additionally validates formatting, Detekt, the model-artifact guard, downloader/installer/backend tests and Android Lint.

## Physical-device GGUF validation

Two validation paths exercise the same production model-store, runtime and backend contracts.

### ADB and instrumentation

Connect a physical `arm64-v8a` device with USB debugging enabled, then run:

```bash
bash scripts/run-device-e2e.sh \
  --model /absolute/path/to/model.gguf \
  --architecture qwen3 \
  --quantization Q4_K_M \
  --memory-repeat 5
```

The model is streamed into the debuggable test application's private storage and is never committed or packaged. See [`docs/device-e2e-testing.md`](docs/device-e2e-testing.md).

### Google Play internal testing without developer mode

`apps/local-llm-phone-test` is a normal launcher application intended for phones where developer mode or ADB is unavailable. It imports a GGUF through Android's Storage Access Framework, runs generation, cancellation and repeated memory cycles, then produces a privacy-safe report that can be copied or shared.

Configure the external PKCS12 upload keystore and macOS Keychain by following [`docs/android-upload-key.md`](docs/android-upload-key.md), then build or sign its release AAB through `scripts/build-phone-test-release.sh`. Upload it to the Google Play internal-testing track, install it with the tester account and follow [`docs/play-internal-phone-test.md`](docs/play-internal-phone-test.md). Signing keys, credentials and GGUF files must never be committed.

The runtime remains not production-ready until representative physical-device evidence covers JNI loading, real GGUF inference, cancellation during prefill and decode, repeated lifecycle stability, memory, latency, throughput and thermal behavior.

See [`docs/architecture.md`](docs/architecture.md), [`docs/implementation-plan.md`](docs/implementation-plan.md), [`docs/definition-of-done.md`](docs/definition-of-done.md), [`docs/current-state.md`](docs/current-state.md), [`docs/model-installation.md`](docs/model-installation.md), [`docs/device-e2e-testing.md`](docs/device-e2e-testing.md), [`docs/play-internal-phone-test.md`](docs/play-internal-phone-test.md) and [`docs/roadmap.md`](docs/roadmap.md).
