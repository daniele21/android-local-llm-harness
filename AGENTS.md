# Android Local LLM Harness — Coding Agent Guide

This file is the stable entry point for coding agents. Current implementation status belongs in [`docs/roadmap.md`](docs/roadmap.md); target behavior belongs in [`docs/implementation-plan.md`](docs/implementation-plan.md).

## Start here

Read these sources before a non-trivial change:

1. [`README.md`](README.md) — purpose, toolchain and top-level structure.
2. [`BRANCHING.md`](BRANCHING.md) — canonical branch and pull-request discipline.
3. [`docs/architecture.md`](docs/architecture.md) — data-plane and control-plane boundaries.
4. [`docs/roadmap.md`](docs/roadmap.md) — current status and next priorities.
5. [`docs/implementation-plan.md`](docs/implementation-plan.md) — target behavior and acceptance criteria.
6. [`docs/definition-of-done.md`](docs/definition-of-done.md) — merge and production readiness.
7. [`docs/api-usage.md`](docs/api-usage.md) — embedded API and lifecycle.
8. [`docs/device-e2e-testing.md`](docs/device-e2e-testing.md) and [`docs/device-e2e-evidence.md`](docs/device-e2e-evidence.md) — physical-device validation.
9. [`docs/adr/README.md`](docs/adr/README.md) — accepted architectural decisions.

When sources disagree, use this precedence: executable contracts and tests, accepted ADRs, architecture, implementation plan, roadmap, README and this guide. Do not silently reconcile contradictions.

## Non-negotiable architecture invariants

- Keep public contracts independent from Android UI, Capacitor and `llama.cpp` types.
- Never expose native pointers, backend structures or backend-owned handles outside the backend module.
- Keep runtime orchestration independent from transport and persistence implementations.
- Resolve models explicitly through `applicationId + useCaseId`; never silently select or substitute a model.
- Use stable identifiers and serializable DTOs at public boundaries.
- Keep one loaded model and one active decode by default until measurements justify another policy.
- Keep prompts and generated content out of normal telemetry.
- Store GGUF artifacts by immutable SHA-256 identity and never commit model binaries.
- Treat cancellation, shutdown and partial failure as normal lifecycle paths.
- Prefer composition and dependency injection over global mutable state.
- Add a module only for a real responsibility, dependency boundary, reuse boundary or independently testable behavior.
- Avoid generic utilities without a domain concept and avoid speculative empty modules.
- Keep one canonical implementation line per active phase.
- Never present simulated acceptance as physical-device or production evidence.

## Repository map

| Path | Responsibility |
| --- | --- |
| `core/contracts` | Stable requests, responses, sessions, metrics and errors |
| `core/runtime-core` | Orchestration, scheduling, model/context/session lifecycle and memory policy |
| `models/model-profile` | Artifacts, load profiles, use-case profiles and app bindings |
| `models/model-store` | Content-addressed storage, import and integrity verification |
| `backends/llama-cpp` | Kotlin/JNI/C++ backend and native resource ownership |
| `observability/contracts` | Stable telemetry, log, health and dashboard schemas |
| `observability/in-memory-store` | Bounded ephemeral telemetry implementation and deterministic tests |
| `observability/room-store` | Persistent Android telemetry, retention and database lifecycle |
| `observability/health-engine` | Health-suite orchestration, model-integrity checks and persisted control-plane results |
| `transports/in-process` | Embedded client-to-runtime delegation |
| `apps/local-llm-console` | Developer console and future cross-app control plane |
| `apps/device-test-runner` | Real-device GGUF lifecycle, cancellation and memory validation |
| `third_party/llama.cpp` | Pinned upstream submodule |
| `scripts` | Repository, packaging, device and evidence validation |
| `docs` | Architecture, plans, roadmap, ADRs, API and operations |

`settings.gradle.kts` is authoritative for the Gradle module list. Run `python3 scripts/verify-agent-navigation.py` after adding, removing or renaming a module.

### Ownership and routing

- Public API changes start in `core/contracts`; inspect all runtime, transport and observability consumers.
- Lifecycle, scheduling and memory changes start in `core/runtime-core`; preserve serialized state mutation and recovery after failure.
- GGUF storage or integrity changes start in `models/model-store` and `models/model-profile`; preserve streaming I/O, atomic staging and SHA-256 identity.
- JNI or generation changes start in `backends/llama-cpp`; preserve coarse-grained JNI calls, opaque handles, idempotent release and cooperative cancellation.
- Telemetry schemas start in `observability/contracts`; persistence stays in `room-store`, ephemeral behavior in `in-memory-store`, and check orchestration in `health-engine`.
- Health checks must return privacy-safe summaries, remain independently testable and persist through `TelemetryRepository`; a check failure must not break inference.
- Console code must not open another application’s private Room database directly. Cross-app access requires the planned signature-protected diagnostics bridge.
- Native and Capacitor integrations must remain thin and must not duplicate model resolution, validation, generation policy, error mapping or telemetry.

## Change workflow

1. Confirm the canonical base and active pull requests.
2. Read relevant contracts, implementation, tests and documentation.
3. Identify the module that owns the behavior.
4. Implement the smallest coherent vertical slice.
5. Add deterministic tests for success, failure and lifecycle paths.
6. Run targeted checks while iterating.
7. Run the aggregate repository validation before merge.
8. Record deferred physical-device evidence explicitly without claiming production readiness.
9. Update architecture or ADRs for boundary changes and the roadmap only after acceptance criteria pass.
10. Keep commits focused on behavior.

A feature is not complete when it duplicates domain logic, couples core code to an integration, exposes native details, lacks isolated tests, makes dependencies hard to replace, or leaves documentation materially stale.

## Validation commands

### Navigation and scripts

```bash
python3 scripts/verify-agent-navigation.py
find scripts -type f -name '*.sh' -exec bash -n {} \;
python3 -m py_compile scripts/*.py
bash scripts/run-device-e2e.sh --help
bash scripts/capture-device-e2e-evidence.sh --help
```

### Kotlin and Android

```bash
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew check
./gradlew lintDebug :apps:local-llm-console:lintInternal
./gradlew assembleDebug :apps:local-llm-console:assembleInternal
./gradlew :observability:room-store:assembleDebugAndroidTest
./gradlew :observability:health-engine:assembleDebug
./gradlew :apps:device-test-runner:assembleDebug :apps:device-test-runner:assembleDebugAndroidTest
python3 scripts/verify-android-packaging.py
```

`./gradlew check` includes the simulated lifecycle, telemetry repository tests and health-engine tests. Telemetry and health failures must never fail or cancel generation.

### Native host tests

```bash
cmake -S backends/llama-cpp/src/test-native -B build/native-tests -DCMAKE_BUILD_TYPE=Release
cmake --build build/native-tests --parallel 2
ctest --test-dir build/native-tests --output-on-failure
```

### Physical-device evidence

```bash
bash scripts/capture-device-e2e-evidence.sh \
  --model /absolute/path/to/model.gguf \
  --architecture <architecture> \
  --quantization <quantization> \
  --memory-repeat 5
```

Physical-device evidence is mandatory before production readiness, application-consumer release or device-performance claims for native loading, generation, cancellation, memory, ABI or JNI changes.

## Testing expectations

- Keep domain logic behind interfaces and use fakes for deterministic orchestration tests.
- Maintain cross-module simulated acceptance with the real model store and runtime orchestrator.
- Add native tests for handle registries, metadata parsing and cancellation behavior.
- Test cleanup after failure and cancellation, not only success.
- Test idempotent close and release behavior.
- Avoid timing assertions without a deterministic clock.
- Test telemetry retention, ordering, terminal replacement and privacy-safe persistence.
- Test health-suite aggregation, unknown checks, unexpected exceptions and persistence.
- Model-integrity checks must not expose private paths, model bytes or arbitrary verification details.

## Maintaining `AGENTS.md`

Use the exact uppercase filename. Keep this guide navigational and durable:

- link to canonical documents instead of duplicating changing status;
- update the repository map whenever `settings.gradle.kts` changes;
- update validation commands when CI changes;
- document new ownership boundaries and link accepted ADRs;
- keep current completion status in `docs/roadmap.md`;
- run the navigation guard after every edit.

Pause and surface the issue rather than improvising when a change conflicts with public contracts or an ADR, would commit a model or native dependency, exposes backend state, duplicates an active implementation line, or would claim production readiness without physical-device evidence.
