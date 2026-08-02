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

## Initial modules

```text
core/contracts                  Stable request, session and runtime contracts
core/runtime-core               Runtime orchestration, scheduling and lifecycle
models/model-profile            GGUF artifacts, load profiles and app bindings
models/model-store              Content-addressed model storage and integrity
backends/llama-cpp              Kotlin/JNI/C++ llama.cpp backend
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

Phase 0 repository hardening is complete.

The Phase 1 development line contains:

- a pinned `llama.cpp` submodule and Android `arm64-v8a` build;
- GGUF metadata inspection;
- SHA-256 content-addressed model import and verification;
- model and context lifecycle management through opaque native handles;
- deterministic generation and aggregated streaming;
- cooperative cancellation;
- a single-decode scheduler with request priorities;
- runtime orchestration, model reuse and model-switch protection;
- base metrics and Android memory-pressure handling;
- Kotlin and native tests for the implemented behavior.

Phase 1 is not production-ready until cumulative CI validation and an end-to-end real-device run with a supported GGUF are complete. See [`docs/roadmap.md`](docs/roadmap.md) for the authoritative consolidation checklist.

## Coding-agent navigation

Coding agents must start from [`AGENTS.md`](AGENTS.md). It maps modules to responsibilities, defines architectural invariants, routes common change types and lists required validation commands.

The repository validates that configured Gradle modules remain discoverable from `AGENTS.md` and that local links in agent guides resolve correctly:

```bash
python3 scripts/verify-agent-navigation.py
```

## Validation

The complete repository gate includes:

```bash
python3 scripts/verify-agent-navigation.py
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew check
./gradlew lintDebug :apps:local-llm-console:lintInternal
./gradlew assembleDebug :apps:local-llm-console:assembleInternal
cmake -S backends/llama-cpp/src/test-native -B build/native-tests -DCMAKE_BUILD_TYPE=Release
cmake --build build/native-tests --parallel 2
ctest --test-dir build/native-tests --output-on-failure
```

## Roadmap

1. Consolidate and validate the functional embedded runtime on CI and a real Android `arm64-v8a` device.
2. Persist structured telemetry and expose run timelines, health, sanity and benchmark results.
3. Add native Android and Capacitor integration surfaces as thin adapters.
4. Add a Binder transport and promote the console app into the shared runtime host.

See [`docs/architecture.md`](docs/architecture.md), [`docs/implementation-plan.md`](docs/implementation-plan.md), [`docs/definition-of-done.md`](docs/definition-of-done.md) and [`docs/roadmap.md`](docs/roadmap.md).
