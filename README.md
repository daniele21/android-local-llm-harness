# Android Local LLM Harness

A local-first Android harness for running explicit GGUF models through `llama.cpp`, embedding the runtime inside native or Capacitor applications today and preserving a clean path toward a shared Android service later.

## Core decisions

- **Model-aware, not model-selecting:** every application/use case binds to an explicit GGUF artifact and runtime profile.
- **GGUF first:** `llama.cpp` is the initial native backend.
- **Embedded first:** applications call the same runtime through an in-process transport.
- **Shared later:** transport boundaries and serializable contracts are kept separate so Binder can replace the in-process transport.
- **Observability first:** runs, timings, health, sanity checks, cache state and structured logs are first-class domains.
- **Privacy by default:** telemetry stores metadata only; prompts and outputs are not persisted by default.

## Initial modules

```text
core/contracts                  Stable request, session and runtime contracts
core/runtime-core               Runtime state machine and orchestration shell
models/model-profile            GGUF artifacts, load profiles and app bindings
models/model-store              Content-addressed model storage contracts
backends/llama-cpp              JNI boundary and native backend bootstrap
observability/contracts         Metrics, logs, health and dashboard snapshots
observability/in-memory-store   Initial local telemetry repository
transports/in-process           Embedded transport implementation
apps/local-llm-console          Developer dashboard application shell
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

## Build prerequisites

- JDK 17
- Android SDK API 36
- Android Build Tools 36.0.0
- Android NDK 28.2.13676358
- Gradle 9.5.0 through the committed wrapper

The repository uses Android Gradle Plugin 9.3.0 and its built-in Kotlin support. Android API 36 is the stable reproducible build target; API 37 remains a preview platform and will be adopted only when the required SDK package is consistently available in CI.

## Current state

The repository establishes module boundaries, contracts, a compilable JNI stub, a minimal developer console and the Phase 0 build-quality foundation. The actual `llama.cpp` source is intentionally not vendored yet; see [`third_party/llama.cpp/README.md`](third_party/llama.cpp/README.md).

## Roadmap

1. Pin and integrate a tested `llama.cpp` commit.
2. Implement GGUF metadata inspection and content-addressed import.
3. Add model/context lifecycle management and cancellation.
4. Persist structured telemetry locally.
5. Add sanity, performance, stability and cache health suites.
6. Create the Capacitor plugin adapter.
7. Add a Binder transport and promote the console app into the shared runtime host.

See [`docs/architecture.md`](docs/architecture.md), [`docs/implementation-plan.md`](docs/implementation-plan.md) and [`docs/roadmap.md`](docs/roadmap.md).
