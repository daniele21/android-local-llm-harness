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

## Modules

```text
core/contracts                  Stable request, session and runtime contracts
core/runtime-core               Runtime orchestration, scheduling, lifecycle and telemetry emission
models/model-profile            GGUF artifacts, load profiles and app bindings
models/model-store              Content-addressed model storage and integrity
backends/llama-cpp              Kotlin/JNI/C++ llama.cpp backend
observability/contracts         Metrics, logs, health, retention and dashboard contracts
observability/in-memory-store   Bounded ephemeral telemetry and deterministic test implementation
observability/room-store        Persistent Android Room telemetry repository
transports/in-process           Embedded transport implementation
apps/local-llm-console          Developer dashboard application shell
apps/device-test-runner         Real-device GGUF lifecycle test application
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

Phase 1 is merged into `main` and provides:

- a pinned `llama.cpp` submodule and Android `arm64-v8a` build;
- GGUF metadata inspection;
- SHA-256 content-addressed model import and verification;
- model and context lifecycle management through opaque native handles;
- deterministic generation and aggregated streaming;
- cooperative cancellation;
- a single-decode scheduler with request priorities;
- runtime orchestration, model reuse and model-switch protection;
- Android memory-pressure handling;
- Kotlin, native, simulated-acceptance and packaging tests;
- a real-device test runner for GGUF lifecycle, cancellation and optional PSS regression checks.

Phase 2 has started with persistent, privacy-safe runtime telemetry:

- `TelemetryRepository` supports bounded run, log and health queries;
- `RoomTelemetryRepository` stores telemetry across process restarts;
- the in-memory repository remains available for ephemeral use and deterministic tests;
- runtime requests persist `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED` or `CANCELLED` state;
- queue, model-load, TTFT, prefill, decode, token and throughput metrics are retained;
- prompt and generated-output content are not persisted;
- telemetry failures cannot fail inference.

The console run/log viewer, memory and thermal snapshots, health and sanity engines, cache suites, benchmark regression history and redacted diagnostic export remain Phase 2 work.

The runtime is not production-ready until the physical-device gate is completed on representative Android `arm64-v8a` devices with supported GGUF models. See [`docs/roadmap.md`](docs/roadmap.md) for authoritative status.

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
./gradlew :observability:room-store:assembleDebugAndroidTest
./gradlew :apps:device-test-runner:assembleDebugAndroidTest
cmake -S backends/llama-cpp/src/test-native -B build/native-tests -DCMAKE_BUILD_TYPE=Release
cmake --build build/native-tests --parallel 2
ctest --test-dir build/native-tests --output-on-failure
```

## Real-device GGUF validation

Connect a physical `arm64-v8a` device with USB debugging enabled, then run:

```bash
bash scripts/run-device-e2e.sh \
  --model /absolute/path/to/model.gguf \
  --architecture qwen2 \
  --quantization Q4_K_M
```

Add `--memory-repeat 5` to execute repeated load/generate/unload cycles with the default PSS-growth budget. The model is streamed into the debuggable test application's private storage and is never committed or packaged.

See [`docs/device-e2e-testing.md`](docs/device-e2e-testing.md) for arguments, evidence requirements and interpretation of the memory check.

## Roadmap

1. Complete the physical-device production-readiness evidence for the merged functional runtime.
2. Continue Phase 2 with timeline views, snapshots, health, sanity, cache and benchmark suites, and diagnostic export.
3. Add native Android and Capacitor integration surfaces as thin adapters.
4. Add a Binder transport and promote the console app into the shared runtime host.

See [`docs/architecture.md`](docs/architecture.md), [`docs/implementation-plan.md`](docs/implementation-plan.md), [`docs/definition-of-done.md`](docs/definition-of-done.md), [`docs/device-e2e-testing.md`](docs/device-e2e-testing.md) and [`docs/roadmap.md`](docs/roadmap.md).
