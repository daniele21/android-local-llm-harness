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
core/contracts                         Stable request, session and runtime contracts
core/runtime-core                      Runtime orchestration, scheduling, lifecycle and telemetry emission
models/model-profile                   GGUF artifacts, load profiles and app bindings
models/model-store                     Content-addressed model storage and integrity
backends/llama-cpp                     Kotlin/JNI/C++ llama.cpp backend
observability/contracts                Metrics, logs, health, retention and dashboard contracts
observability/in-memory-store          Bounded ephemeral telemetry and deterministic test implementation
observability/room-store               Persistent Android Room telemetry repository
observability/health-engine            Health orchestration, model integrity, sanity and cache checks
observability/android-resource-probe   Android memory and thermal snapshot provider
observability/benchmark-engine         Baseline capture and performance regression checks
transports/in-process                  Embedded transport implementation
apps/local-llm-console                 Developer dashboard application shell
apps/device-test-runner                ADB/instrumentation GGUF lifecycle test application
apps/local-llm-phone-test              Play-installable physical-device validation application
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
- an ADB/instrumentation runner for GGUF lifecycle, cancellation and optional PSS regression checks;
- an ARM64 emulator preflight with a real Qwen3 0.6B GGUF.

Phase 2 is substantially implemented in `main`:

- `TelemetryRepository` supports bounded run, log, health, resource and benchmark queries;
- `RoomTelemetryRepository` persists telemetry across process restarts with non-destructive schema migrations;
- runtime requests persist `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED` or `CANCELLED` state;
- queue, model-load, TTFT, prefill, decode, token and throughput metrics are retained;
- model loads are classified explicitly as `COLD`, `WARM` or `UNKNOWN`;
- Android process PSS, native heap, Java heap, available-memory and thermal snapshots can be captured explicitly;
- the health engine supports model-integrity, generation-sanity and model-integrity-cache checks;
- generation sanity supports non-empty, exact, required-marker, forbidden-marker and regex assertions;
- benchmark baselines and regression checks are persisted separately for cold and warm runs;
- prompt and generated-output content are not persisted;
- telemetry failures cannot fail inference.

The main remaining Phase 2 work is:

- a functional console run timeline and structured-log viewer;
- console views and controls for health, resources, cache and benchmarks;
- health contracts for future tokenizer, prompt, KV or downloaded-model caches;
- a privacy-redacted diagnostic bundle export;
- the signature-protected diagnostics bridge required for cross-application console access.

The runtime is not production-ready until the physical-device gate is completed on representative Android `arm64-v8a` devices with supported GGUF models. Host, simulated and emulator tests do not prove OEM memory management, physical-device native stability or representative thermal performance. See [`docs/roadmap.md`](docs/roadmap.md) for authoritative status.

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
./gradlew lintDebug :apps:local-llm-console:lintInternal :apps:local-llm-phone-test:lintRelease
./gradlew assembleDebug :apps:local-llm-console:assembleInternal
LOCAL_LLM_PHONE_TEST_ALLOW_UNSIGNED_RELEASE=true \
  ./gradlew :apps:local-llm-phone-test:assembleDebug :apps:local-llm-phone-test:bundleRelease
./gradlew :observability:room-store:assembleDebugAndroidTest
./gradlew :observability:health-engine:assembleDebug
./gradlew :observability:android-resource-probe:assembleDebug
./gradlew :observability:benchmark-engine:assembleDebug
./gradlew :apps:device-test-runner:assembleDebugAndroidTest
cmake -S backends/llama-cpp/src/test-native -B build/native-tests -DCMAKE_BUILD_TYPE=Release
cmake --build build/native-tests --parallel 2
ctest --test-dir build/native-tests --output-on-failure
```

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

Configure the external PKCS12 upload keystore and macOS Keychain once by following [`docs/android-upload-key.md`](docs/android-upload-key.md), then build or sign its release AAB through `scripts/build-phone-test-release.sh`. Upload it to the Google Play internal-testing track, install it with the tester account and follow [`docs/play-internal-phone-test.md`](docs/play-internal-phone-test.md). Signing keys, credentials and GGUF files must never be committed.

## Roadmap

1. Complete the physical-device production-readiness evidence for the merged functional runtime.
2. Build the developer-console views and signature-protected diagnostics bridge over the Phase 2 data already available.
3. Add the privacy-redacted diagnostic export and future cache-health contracts.
4. Add native Android and Capacitor integration surfaces as thin adapters.
5. Add a Binder transport and promote the console app into the shared runtime host.

See [`docs/architecture.md`](docs/architecture.md), [`docs/implementation-plan.md`](docs/implementation-plan.md), [`docs/definition-of-done.md`](docs/definition-of-done.md), [`docs/device-e2e-testing.md`](docs/device-e2e-testing.md), [`docs/play-internal-phone-test.md`](docs/play-internal-phone-test.md) and [`docs/roadmap.md`](docs/roadmap.md).
