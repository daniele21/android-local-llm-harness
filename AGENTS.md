# Android Local LLM Harness — Coding Agent Guide

This file is the stable entry point for coding agents working in this repository. It describes authoritative sources, module ownership, architectural invariants and required validation. Current implementation status belongs in [`docs/roadmap.md`](docs/roadmap.md), not here.

## Start here

Read these documents before making a non-trivial change:

1. [`README.md`](README.md) — product purpose, toolchain and top-level module map.
2. [`docs/architecture.md`](docs/architecture.md) — data plane, control plane and runtime boundaries.
3. [`docs/roadmap.md`](docs/roadmap.md) — current status and next priorities.
4. [`docs/implementation-plan.md`](docs/implementation-plan.md) — target behavior and acceptance criteria.
5. [`docs/definition-of-done.md`](docs/definition-of-done.md) — repository-wide completion requirements.
6. [`docs/device-e2e-testing.md`](docs/device-e2e-testing.md) — real-device GGUF validation.
7. [`docs/adr/README.md`](docs/adr/README.md) — architectural decision records.

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

## Repository map

| Path | Responsibility | Typical changes |
| --- | --- | --- |
| `core/contracts` | Stable requests, responses, sessions, metrics and errors | Public API evolution and serializable DTOs |
| `core/runtime-core` | Orchestration, scheduling, model/context/session lifecycle and memory policy | State transitions, queueing, generation and recovery |
| `models/model-profile` | Artifacts, load profiles, use-case profiles and app bindings | Explicit configuration and validation |
| `models/model-store` | Content-addressed storage and integrity | Import, verification, deduplication and cleanup |
| `backends/llama-cpp` | Kotlin/JNI/C++ backend | Native lifecycle, GGUF inspection, generation and cancellation |
| `observability/contracts` | Telemetry, logs, health and dashboard contracts | Stable observability schemas |
| `observability/in-memory-store` | Initial telemetry implementation | Repository behavior and test doubles |
| `transports/in-process` | Embedded transport | Client-to-runtime delegation |
| `apps/local-llm-console` | Developer console and future control plane | Diagnostics UI and runtime inspection |
| `apps/device-test-runner` | Real-device Phase 1 validation app | GGUF lifecycle, cancellation and memory instrumentation tests |
| `third_party/llama.cpp` | Pinned upstream submodule | Controlled pin updates only |
| `scripts` | Reproducible repository and device validation | CI guards and host runners |
| `docs` | Architecture, plan, roadmap, ADRs and operations | Durable decisions and guidance |

`settings.gradle.kts` is authoritative for the Gradle module list. Run `python3 scripts/verify-agent-navigation.py` after adding, removing or renaming a module.

## Task routing

### Public contract change

Start in `core/contracts`. Inspect consumers in `core/runtime-core`, transports and observability. Preserve serialization and future Binder compatibility. Never put backend-specific types into public contracts.

### Runtime lifecycle or scheduling change

Start in `core/runtime-core`. Inspect `RuntimeOrchestrator.kt`, `InferenceBackend.kt`, `SingleDecodeScheduler.kt`, session ownership, cancellation, memory-pressure paths and fake-backend tests. State mutations must remain serialized, and a failed request must leave the runtime recoverable.

### GGUF storage or integrity change

Start in `models/model-store` and `models/model-profile`. Preserve streaming I/O, atomic staging, SHA-256 identity, duplicate detection, active-model protection and typed failures. Never read a complete model into memory merely to import or hash it.

### `llama.cpp`, JNI or generation change

Start in `backends/llama-cpp`. Preserve coarse-grained JNI calls, opaque handles, idempotent release, cleanup after partial failures, aggregated streaming, cooperative cancellation and typed Kotlin error mapping.

Synchronous and streaming generation must share tokenization, context validation, sampler construction, prefill, decode, token conversion, metrics, error mapping and resource ownership. Do not include implementation `.cpp` files from other `.cpp` files.

### Observability or console change

Start with `observability/contracts`. Store identifiers, sizes, timings, token counts, statuses and error codes by default; do not persist prompts or generated content without a separately designed diagnostic mode.

### Android or Capacitor integration

Keep product behavior in core modules and integrations thin. Do not duplicate model resolution, generation policy, validation, error mapping or telemetry across surfaces.

### Real-device validation change

Start in `apps/device-test-runner`, `scripts/run-device-e2e.sh` and [`docs/device-e2e-testing.md`](docs/device-e2e-testing.md). Keep GGUF files outside the repository and APKs. Device checks must use production store, runtime and backend implementations rather than test doubles.

## Change workflow

1. Inspect the relevant contracts, implementation, tests and documentation.
2. Identify the module that owns the behavior.
3. Implement the smallest coherent change without speculative abstractions.
4. Add tests for normal, failure, cancellation and lifecycle paths as applicable.
5. Run targeted checks while iterating.
6. Run the complete relevant validation before merge.
7. Update the correct source of truth in the same change.
8. Keep commits focused and describe behavior rather than file movement.

Do not mark a roadmap item complete before its acceptance criteria and required evidence are satisfied.

## Validation commands

### Repository navigation

```bash
python3 scripts/verify-agent-navigation.py
```

### Kotlin and JVM

```bash
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew check
```

### Android build and lint

```bash
./gradlew lintDebug :apps:local-llm-console:lintInternal
./gradlew assembleDebug :apps:local-llm-console:assembleInternal
./gradlew :apps:device-test-runner:assembleDebug :apps:device-test-runner:assembleDebugAndroidTest
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

### Real Android device

```bash
bash scripts/run-device-e2e.sh \
  --model /absolute/path/to/model.gguf \
  --architecture <architecture> \
  --quantization <quantization>
```

Add `--memory-repeat 5` for repeated lifecycle and PSS regression validation. Real-device evidence is required for changes touching native loading, generation, cancellation, memory management, ABI packaging or JNI behavior.

## Testing expectations

- Put domain logic behind interfaces so orchestration can be tested without loading a model where practical.
- Use fake backends for deterministic queueing, failure and memory-pressure tests.
- Add native tests for handle registries, metadata parsing, cancellation registries and pure C++ behavior.
- Use `apps/device-test-runner` for JNI linkage, ABI packaging, real GGUF compatibility, streaming, cancellation and repeated lifecycle tests.
- Test cleanup after failures and cancellation, not only successful output.
- Test idempotent close and release behavior.
- Avoid timing-dependent assertions unless a deterministic clock is owned by the test; real-device timeouts must be configurable.

## Documentation update matrix

| Change | Documentation to update |
| --- | --- |
| Module boundary or dependency direction | `docs/architecture.md`, usually an ADR, and this guide |
| New public API or lifecycle behavior | Feature documentation and `docs/implementation-plan.md` when scope changes |
| Completed or deferred work | `docs/roadmap.md` |
| Irreversible architectural choice | New ADR and ADR index |
| New validation command or guard | This guide, relevant operational document and CI |
| New module | `settings.gradle.kts`, repository map, tests and architecture docs |
| Device test behavior or arguments | `docs/device-e2e-testing.md` and `scripts/run-device-e2e.sh` |
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
- required Android/NDK tooling or real-device validation is unavailable.

A partial, explicitly documented result is preferable to claiming completion without required evidence.
