# Android Local LLM Harness — Coding Agent Guide

This file is the stable entry point for coding agents working in this repository. It describes authoritative sources, module ownership, architectural invariants and required validation. Current implementation status belongs in [`docs/roadmap.md`](docs/roadmap.md), not here.

## Start here

Read these documents before making a non-trivial change:

1. [`README.md`](README.md) — product purpose, toolchain and top-level module map.
2. [`BRANCHING.md`](BRANCHING.md) — canonical implementation line, pull-request and branch discipline.
3. [`docs/architecture.md`](docs/architecture.md) — data plane, control plane and runtime boundaries.
4. [`docs/roadmap.md`](docs/roadmap.md) — current status and next priorities.
5. [`docs/implementation-plan.md`](docs/implementation-plan.md) — target behavior and acceptance criteria.
6. [`docs/definition-of-done.md`](docs/definition-of-done.md) — merge-readiness and production-readiness requirements.
7. [`docs/api-usage.md`](docs/api-usage.md) — implemented embedded API, lifecycle and usage examples.
8. [`docs/device-e2e-testing.md`](docs/device-e2e-testing.md) — real-device GGUF validation.
9. [`docs/device-e2e-evidence.md`](docs/device-e2e-evidence.md) — privacy-safe acceptance-evidence collection.
10. [`docs/adr/README.md`](docs/adr/README.md) — architectural decision records.

When sources disagree, use this precedence:

1. public contracts and executable tests;
2. accepted ADRs;
3. `docs/architecture.md`;
4. `docs/implementation-plan.md`;
5. `docs/roadmap.md`;
6. `README.md` and this guide.

Do not silently reconcile contradictions. Surface them and update the correct source of truth in the same change.

## Product intent

The project is reusable Android infrastructure for running explicit local GGUF models through `llama.cpp`. The runtime is embedded in native or Capacitor applications first while preserving a transport boundary for a future shared Android service.

Request resolution is explicit:

```text
applicationId + useCaseId
        -> AppModelBinding
        -> UseCaseProfile
        -> GgufModelProfile
        -> exact GGUF artifact digest
        -> exact llama.cpp configuration
```

The harness must never silently select or substitute a model.

## Non-negotiable architecture invariants

- Keep public contracts independent from Android UI, Capacitor and `llama.cpp` implementation types.
- Never expose native pointers, `llama.cpp` structures or backend-owned handles outside the backend module.
- Keep runtime orchestration independent from transport implementations.
- Keep model selection explicit through application/use-case bindings.
- Use stable identifiers and serializable DTOs at public boundaries.
- Default to one loaded model and one active decode until benchmarks justify another policy.
- Keep prompts and generated content out of telemetry by default.
- Store GGUF artifacts by immutable SHA-256 identity and never commit model binaries.
- Treat cancellation, shutdown and partial failure as normal resource-lifecycle paths.
- Prefer composition and dependency injection over global mutable state.
- Add a module only for a real responsibility, dependency boundary, reuse boundary or independently testable behavior.
- Do not create generic utilities without a clear domain concept merely to remove duplication.
- Keep one canonical implementation line per active phase; do not revive superseded branches or PRs.
- Never present simulated acceptance as physical-device or production evidence.

## Repository map

| Path | Responsibility | Typical changes |
| --- | --- | --- |
| `core/contracts` | Stable requests, responses, sessions, metrics and errors | Public API evolution and serializable DTOs |
| `core/runtime-core` | Orchestration, scheduling, model/context/session lifecycle and memory policy | State transitions, queueing, generation and recovery |
| `models/model-profile` | Artifacts, load profiles, use-case profiles and app bindings | Explicit configuration and validation |
| `models/model-store` | Content-addressed storage and integrity | Import, verification, deduplication and cleanup |
| `backends/llama-cpp` | Kotlin/JNI/C++ backend | Native lifecycle, GGUF inspection, generation and cancellation |
| `observability/contracts` | Telemetry, logs, health and dashboard contracts | Stable observability schemas |
| `observability/in-memory-store` | Bounded in-memory telemetry implementation | Repository behavior, tests and ephemeral diagnostics |
| `observability/room-store` | Persistent Android Room telemetry implementation | Run timelines, structured logs, retention and database lifecycle |
| `transports/in-process` | Embedded transport | Client-to-runtime delegation |
| `apps/local-llm-console` | Developer console and future control plane | Diagnostics UI and runtime inspection |
| `apps/device-test-runner` | Real-device Phase 1 validation app | GGUF lifecycle, cancellation and memory instrumentation tests |
| `third_party/llama.cpp` | Pinned upstream submodule | Controlled pin updates only |
| `scripts` | Reproducible repository and device validation | CI guards, packaging checks, host runners and evidence capture |
| `docs` | Architecture, plan, roadmap, ADRs, API and operations | Durable decisions and guidance |

`settings.gradle.kts` is authoritative for the Gradle module list. Run `python3 scripts/verify-agent-navigation.py` after adding, removing or renaming a module.

## Task routing

### Branch, pull-request or release-line change

Start with [`BRANCHING.md`](BRANCHING.md) and inspect all open pull requests before creating another implementation branch. Do not create parallel work for a responsibility already owned by an active PR. Keep Dependabot and other dependency-only updates separate from functional runtime changes.

### Public contract change

Start in `core/contracts`. Inspect consumers in `core/runtime-core`, transports and observability. Preserve serialization and future Binder compatibility. Never put backend-specific types into public contracts.

### Runtime lifecycle or scheduling change

Start in `core/runtime-core`. Inspect `RuntimeOrchestrator.kt`, `InferenceBackend.kt`, `SingleDecodeScheduler.kt`, `Phase1SimulatedAcceptanceTest.kt`, session ownership, cancellation, memory-pressure paths and fake-backend tests. State mutations must remain serialized, and a failed request must leave the runtime recoverable.

### GGUF storage or integrity change

Start in `models/model-store` and `models/model-profile`. Preserve streaming I/O, atomic staging, SHA-256 identity, duplicate detection, active-model protection and typed failures. Never read a complete model into memory merely to import or hash it.

### `llama.cpp`, JNI or generation change

Start in `backends/llama-cpp`. Preserve coarse-grained JNI calls, opaque handles, idempotent release, cleanup after partial failures, aggregated streaming, cooperative cancellation and typed Kotlin error mapping.

Synchronous and streaming generation must share tokenization, context validation, sampler construction, prefill, decode, token conversion, metrics, error mapping and resource ownership. Do not include implementation `.cpp` files from other `.cpp` files.

### Observability or console change

Start with `observability/contracts`. Use `observability/in-memory-store` for bounded ephemeral behavior and deterministic tests; use `observability/room-store` for persistent Android telemetry. Store identifiers, sizes, timings, token counts, statuses and error codes by default; do not persist prompts or generated content without a separately designed diagnostic mode. Telemetry failures must not break inference.

### Android or Capacitor integration

Keep product behavior in core modules and integrations thin. Do not duplicate model resolution, generation policy, validation, error mapping or telemetry across surfaces. Keep [`docs/api-usage.md`](docs/api-usage.md) aligned with public API and lifecycle behavior.

### Android packaging change

Start in `backends/llama-cpp`, the consuming app module and `scripts/verify-android-packaging.py`. Preserve exact ABI ownership, fail on missing or unexpected native libraries and verify the ELF architecture rather than trusting filenames.

### Real-device validation change

Start in `apps/device-test-runner`, `scripts/run-device-e2e.sh`, `scripts/capture-device-e2e-evidence.sh`, [`docs/device-e2e-testing.md`](docs/device-e2e-testing.md) and [`docs/device-e2e-evidence.md`](docs/device-e2e-evidence.md). Keep GGUF files outside the repository, APKs and evidence bundles. Device checks must use production store, runtime and backend implementations rather than test doubles.

## Change workflow

1. Confirm the canonical base and active pull request in `BRANCHING.md` and GitHub.
2. Inspect the relevant contracts, implementation, tests and documentation.
3. Identify the module that owns the behavior.
4. Implement the smallest coherent change without speculative abstractions.
5. Add tests for normal, failure, cancellation and lifecycle paths as applicable.
6. Run targeted checks while iterating.
7. Run the complete relevant merge-readiness validation.
8. Record any deferred physical-device gate explicitly without claiming production readiness.
9. Update the correct source of truth in the same change.
10. Keep commits focused and describe behavior rather than file movement.

Do not mark a roadmap item complete before its acceptance criteria and required evidence are satisfied.

## Validation commands

### Repository navigation and scripts

```bash
python3 scripts/verify-agent-navigation.py
find scripts -type f -name '*.sh' -exec bash -n {} \;
python3 -m py_compile scripts/*.py
bash scripts/run-device-e2e.sh --help
bash scripts/capture-device-e2e-evidence.sh --help
```

### Kotlin, JVM and simulated acceptance

```bash
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew check
```

`./gradlew check` includes the Phase 1 simulated acceptance lifecycle using the real `FileSystemModelStore`, real `RuntimeOrchestrator` and a deterministic simulated backend. It also compiles Room annotation processing and runs the Phase 2 telemetry repository and runtime-lifecycle tests.

### Android build, lint and packaging

```bash
./gradlew lintDebug :apps:local-llm-console:lintInternal
./gradlew assembleDebug :apps:local-llm-console:assembleInternal
./gradlew :observability:room-store:assembleDebugAndroidTest
./gradlew :apps:device-test-runner:assembleDebug :apps:device-test-runner:assembleDebugAndroidTest
python3 scripts/verify-android-packaging.py
```

### Native host tests

```bash
cmake \
  -S backends/llama-cpp/src/test-native \
  -B build/native-tests \
  -DCMAKE_BUILD_TYPE=Release
cmake --build build/native-tests --parallel 2
ctest --test-dir build/native-tests --output-on-failure
```

### Real Android device production evidence

```bash
bash scripts/capture-device-e2e-evidence.sh \
  --model /absolute/path/to/model.gguf \
  --architecture <architecture> \
  --quantization <quantization> \
  --memory-repeat 5
```

Real-device evidence is mandatory before production readiness, application-consumer release or device-performance claims for changes touching native loading, generation, cancellation, memory management, ABI packaging or JNI behavior. It may be deferred until after merge only under the explicit conditions in [`docs/definition-of-done.md`](docs/definition-of-done.md).

## Testing expectations

- Put domain logic behind interfaces so orchestration can be tested without loading a model where practical.
- Use fake backends for deterministic queueing, failure and memory-pressure tests.
- Maintain a cross-module simulated acceptance test using the real model store and runtime orchestrator.
- Add native tests for handle registries, metadata parsing, cancellation registries and pure C++ behavior.
- Verify APK/AAR library names, ABI isolation and ELF architecture in CI.
- Use `apps/device-test-runner` for JNI linkage, real GGUF compatibility, streaming, cancellation and repeated lifecycle tests.
- Test cleanup after failures and cancellation, not only successful output.
- Test idempotent close and release behavior.
- Avoid timing-dependent assertions unless a deterministic clock is owned by the test; real-device timeouts must be configurable.
- Test telemetry retention, ordering, terminal-state replacement and privacy-safe field persistence.
- Ensure a failing telemetry implementation cannot fail or cancel a generation.

## Documentation update matrix

| Change | Documentation to update |
| --- | --- |
| Branch, PR stacking or merge process | `BRANCHING.md`, and this guide when routing changes |
| Module boundary or dependency direction | `docs/architecture.md`, usually an ADR, and this guide |
| New public API or lifecycle behavior | `docs/api-usage.md` and `docs/implementation-plan.md` when scope changes |
| Completed, deferred or remaining work | `docs/roadmap.md` |
| Merge-readiness or production-readiness policy | `docs/definition-of-done.md`, `docs/roadmap.md` and this guide |
| Irreversible architectural choice | New ADR and ADR index |
| New validation command or guard | This guide, relevant operational document and CI |
| New module | `settings.gradle.kts`, repository map, tests and architecture docs |
| Device test behavior or arguments | `docs/device-e2e-testing.md`, `docs/device-e2e-evidence.md` and related scripts |
| Feature completion | `docs/definition-of-done.md` and feature-specific evidence |

## Maintaining `AGENTS.md`

Use the exact uppercase filename `AGENTS.md`. Do not add competing root files such as `agent.md` or `agents.md`.

Keep this guide stable and navigational:

- link to canonical documents instead of duplicating them;
- describe durable invariants, ownership and commands;
- keep current status in `docs/roadmap.md`;
- update the repository map whenever `settings.gradle.kts` changes;
- update task routing when ownership changes;
- update validation commands when CI or device tooling changes;
- keep links relative;
- run `python3 scripts/verify-agent-navigation.py` after every edit.

Add a nested `AGENTS.md` only when a subtree has substantial additional rules. A nested guide may refine but must not contradict root invariants, and it must be linked from this file.

## Stop conditions

Pause and surface the issue rather than improvising when:

- a requested change conflicts with a public contract or accepted ADR;
- documentation and executable behavior disagree materially;
- a model or upstream native dependency would need to be committed directly;
- a change requires exposing backend-native state through public APIs;
- tests show unbounded memory growth, use-after-close, data races or unrecoverable runtime state;
- physical-device validation is unavailable and the requested action would claim production readiness or release the runtime to consumers;
- a proposed branch or PR duplicates an active implementation line.

A partial, explicitly documented result is preferable to claiming completion without required evidence.
