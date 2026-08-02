# Definition of Done

A feature is complete only when its behavior, resource lifecycle, tests, observability and documentation are all complete.

Passing compilation alone is not sufficient.

## Completion levels

The repository distinguishes two evidence levels:

1. **Merge readiness** — deterministic automated tests, simulated acceptance, static analysis, Android builds and binary packaging checks pass from a clean checkout.
2. **Production readiness** — all merge-readiness evidence passes and representative physical Android devices complete the real-GGUF lifecycle, cancellation, memory and JNI-loading gates.

A merge-ready native feature may be integrated while physical-device evidence is explicitly deferred in the roadmap. It must not be described as production-ready, released to application consumers or used for device-performance claims until that evidence exists.

## Functional completion

- The intended behavior is implemented through the correct architectural boundary.
- Public behavior is expressed through stable contracts rather than implementation details.
- Normal, invalid-input, failure, cancellation and shutdown behavior is defined where relevant.
- Partial failures leave the runtime in a recoverable state.
- Resource ownership and release paths are explicit.
- The implementation does not silently substitute models, profiles or policies.

## Modularity and maintainability

The project must remain modular, extensible and maintainable.

Every module and component must have a clear, limited responsibility. Dependencies must follow an explicit direction and must not couple unrelated architectural layers.

A feature is not complete when it:

- duplicates existing domain logic;
- couples `core/runtime-core` to Android UI, Capacitor or `llama.cpp` internals;
- exposes native pointers, handles or backend structures through public APIs;
- adds classes with unclear or mixed responsibilities;
- requires the same domain behavior to be reimplemented in multiple integrations;
- hides behavior in generic utilities without a clear domain concept;
- introduces a speculative or empty module without implemented ownership;
- makes a dependency difficult to replace or fake in tests;
- increases architectural complexity without updating the relevant documentation.

Prefer composition and dependency injection over global mutable state. Keep public APIs small, stable and documented. Backend, transport, model store, scheduler, telemetry store and cache policies must remain replaceable behind explicit interfaces where replacement is an intended architectural capability.

Create a new module only when at least one of these conditions is real and current:

1. it owns an autonomous responsibility;
2. it establishes a necessary dependency boundary;
3. it contains behavior reused by more than one consumer;
4. it isolates a platform or third-party integration;
5. it requires an independent testing or release boundary.

Do not create modules merely to mirror a future architecture diagram.

## Shared generation behavior

Synchronous generation and streaming must reuse the same implementation for:

- tokenization;
- context-limit validation;
- chat-template and prompt preparation;
- sampler construction;
- prefill;
- decode;
- token-to-text conversion;
- stop-condition handling;
- metric collection;
- typed error mapping;
- temporary resource ownership and cleanup.

They may differ in how output is delivered and how cancellation is surfaced to the caller.

Native code must be split by responsibility and linked normally through CMake. Do not include implementation `.cpp` files from other `.cpp` files to share behavior.

## Test completion

- New behavior has isolated automated tests at the lowest useful layer.
- Regression tests fail before the fix and pass after it when the change fixes a defect.
- Failure and cleanup paths are tested, not only the successful result.
- Cancellation is tested while queued and during active work when relevant.
- Handle close/release operations are tested for idempotency.
- Runtime state recovery is tested after backend or request failure.
- Model-store changes test interruption, duplicate import and integrity failure where relevant.
- Native changes include C++ tests for pure native behavior and Kotlin tests for the bridge contract.
- The merge gate exercises the real content-addressed store and runtime orchestrator through a deterministic simulated backend.
- The simulated acceptance lifecycle covers import, verification, prepare, session creation, streaming, active cancellation, recovery, context release, memory-pressure unload, reload and shutdown.
- APK/AAR verification checks the exact native library set, ABI isolation and AArch64 ELF headers.
- Android/JNI/ABI changes are validated on an `arm64-v8a` device with a real supported GGUF before production readiness is claimed.
- Real-device validation uses the production model store, runtime orchestrator and backend rather than fake implementations.
- Device evidence includes GGUF inspection, import verification, load, context creation, streaming generation, cancellation, release, unload and shutdown.

## Validation completion

The relevant narrow checks pass during development, and the complete repository gate passes before merge:

```bash
python3 scripts/verify-agent-navigation.py
python3 -m py_compile scripts/*.py
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew check
./gradlew lintDebug :apps:local-llm-console:lintInternal
./gradlew :apps:device-test-runner:assembleDebug :apps:device-test-runner:assembleDebugAndroidTest
python3 scripts/verify-android-packaging.py
cmake -S backends/llama-cpp/src/test-native -B build/native-tests -DCMAKE_BUILD_TYPE=Release
cmake --build build/native-tests --parallel 2
ctest --test-dir build/native-tests --output-on-failure
```

CI must pass from a clean checkout with the pinned JDK, Android SDK, NDK, Gradle and `llama.cpp` source.

Changes that affect Android native loading, GGUF compatibility, generation, cancellation, memory ownership or ABI packaging additionally require the following before production release or production-readiness claims:

```bash
bash scripts/capture-device-e2e-evidence.sh \
  --model /absolute/path/to/model.gguf \
  --architecture <architecture> \
  --quantization <quantization> \
  --memory-repeat 5
```

A physical-device gate may be deferred until after merge only when:

- the simulated acceptance and packaging gates pass;
- the deferral is explicit in `docs/roadmap.md` and the pull request;
- no production-readiness or performance claim is made;
- the device runner and evidence capture tooling are already present;
- release or downstream application adoption remains blocked on the physical evidence.

Use a device-specific PSS budget when the change can affect model, context or native allocation lifetime. Store the device/model matrix and privacy-safe test output as release evidence; do not commit the GGUF.

## Observability and privacy completion

- Important lifecycle operations emit the required typed events and metrics.
- Failures use stable error codes or typed failures where a contract exists.
- Timings and statuses are recorded without persisting prompt or output content by default.
- New diagnostic data has an explicit retention and privacy policy.
- A behavior that affects model resolution, cache use, queueing, loading or cancellation is visible in diagnostics.

## Documentation completion

The same change updates the appropriate source of truth:

- architecture and dependency boundaries in `docs/architecture.md`;
- implementation scope or acceptance criteria in `docs/implementation-plan.md`;
- completed, deferred or remaining work in `docs/roadmap.md`;
- durable architectural decisions in `docs/adr/`;
- coding-agent navigation, module ownership or validation commands in `AGENTS.md`;
- device procedure and evidence requirements in `docs/device-e2e-testing.md`;
- public usage documentation and examples when APIs change.

Feature documentation must cover, as applicable:

- objective and problem solved;
- public behavior and API;
- lifecycle and resource ownership;
- threading and cancellation;
- errors and recovery;
- observability;
- files and modules involved;
- tests and validation evidence;
- limitations and deferred behavior;
- a minimal usage example.

Do not mark a roadmap item complete while its required evidence is missing. A deferred physical-device item must remain visibly incomplete until the evidence is captured.

## Merge checklist

Before merge, verify:

```text
clear ownership and responsibilities
unidirectional dependencies
no significant domain duplication
small and stable public APIs
isolated and deterministic tests
simulated end-to-end lifecycle passing
recoverable cancellation and failure paths
safe native resource lifecycle
exact Android native packaging verified
privacy-safe observability
documentation and deferred gates updated
all required merge-readiness validation gates passing
```

When physical-device validation is deferred, the merge record must state that production readiness is still blocked.

When duplication appears, identify the shared domain concept first and only then extract a reusable component. Do not move unrelated code into a generic helper merely to reduce line count.
