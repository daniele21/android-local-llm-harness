# Android Local LLM Harness — Coding Agent Guide

This file is the stable entry point for coding agents working in this repository.
It explains where authoritative information lives, how to navigate the modules, which invariants must not be broken, and which checks are required before a change is considered complete.

Do not use this file as a changelog or duplicate detailed implementation status here. Current progress belongs in [`docs/roadmap.md`](docs/roadmap.md).

## Start here

Read these documents in order before making a non-trivial change:

1. [`README.md`](README.md) — product purpose, supported toolchain and top-level module map.
2. [`docs/architecture.md`](docs/architecture.md) — data plane, control plane, lifecycle boundaries and runtime invariants.
3. [`docs/roadmap.md`](docs/roadmap.md) — implemented work, consolidation gates and next priorities.
4. [`docs/implementation-plan.md`](docs/implementation-plan.md) — target behavior, phase deliverables and acceptance criteria.
5. [`docs/definition-of-done.md`](docs/definition-of-done.md) — quality, test and documentation requirements for every feature.
6. [`docs/adr/README.md`](docs/adr/README.md) — architectural decision records and when to add one.

When documents disagree, use this precedence:

1. public contracts and executable tests;
2. accepted ADRs;
3. `docs/architecture.md`;
4. `docs/implementation-plan.md`;
5. `docs/roadmap.md`;
6. `README.md` and this navigation guide.

Do not silently reconcile contradictions. Call them out and update the relevant source of truth in the same change.

## Product intent

The project is a reusable Android infrastructure for running explicit local GGUF models through `llama.cpp`.

The runtime is embedded in native or Capacitor applications first, while preserving a path to a future shared Android service. It is privacy-first, model-aware rather than model-selecting, observable, testable and designed for resource-constrained devices.

Request resolution is explicit:

```text
applicationId + useCaseId
        -> AppModelBinding
        -> UseCaseProfile
        -> GgufModelProfile
        -> exact GGUF artifact digest
        -> exact llama.cpp configuration
```

The harness must never silently substitute a model.

## Non-negotiable architecture invariants

- Keep public contracts independent from Android UI, Capacitor and `llama.cpp` implementation types.
- Never expose native pointers, `llama.cpp` structures or backend-owned handles outside the backend module.
- Keep the runtime data plane independent from the transport. Embedded and future Binder deployments must execute the same orchestration logic.
- Keep model selection explicit through application/use-case bindings.
- Use stable identifiers and serializable DTOs at public boundaries.
- Default to one loaded model and one active decode until measurement justifies a different policy.
- Keep prompts and generated content out of telemetry by default.
- Store GGUF artifacts by immutable SHA-256 identity and never commit model binaries.
- Treat cancellation, shutdown and partial failure as resource-lifecycle paths, not exceptional afterthoughts.
- Prefer composition and dependency injection over global mutable state.
- Add a module only when it owns a real responsibility, dependency boundary, reuse boundary or independently testable behavior.
- Do not fix duplication by creating generic utilities without a clear domain concept.

## Repository map

| Path | Responsibility | Typical changes |
| --- | --- | --- |
| `core/contracts` | Stable public requests, responses, events, sessions, metrics and errors | Public API evolution, serializable DTOs |
| `core/runtime-core` | Runtime orchestration, scheduling, session/model/context lifecycle and memory policy | State transitions, generation flow, queueing, recovery |
| `models/model-profile` | GGUF artifacts, load profiles, use-case profiles and app bindings | Binding rules, profile validation, explicit configuration |
| `models/model-store` | Content-addressed storage and artifact integrity | Import, verification, deduplication, cleanup |
| `backends/llama-cpp` | Kotlin/JNI/C++ backend implementation | GGUF inspection, native lifecycle, generation, streaming, cancellation |
| `observability/contracts` | Telemetry, logging, health and dashboard contracts | Stable observability schemas |
| `observability/in-memory-store` | Initial local telemetry implementation | Repository behavior and test doubles |
| `transports/in-process` | Embedded transport implementation | Client-to-runtime delegation |
| `apps/local-llm-console` | Developer console shell and future control plane | Diagnostics UI and runtime inspection |
| `third_party/llama.cpp` | Pinned upstream submodule | Pin updates only; avoid untracked local edits |
| `scripts` | Repository guards and reproducible validation helpers | CI-safe verification tooling |
| `docs` | Architecture, implementation plan, roadmap, ADRs and completion rules | Decisions, boundaries, status and operational guidance |

The Gradle module list in `settings.gradle.kts` is authoritative. Run `python3 scripts/verify-agent-navigation.py` after adding, removing or renaming a module; CI verifies that every configured module remains discoverable from this file.

## Task routing

### Public API or contract change

Start in `core/contracts`.

Also inspect:

- consumers in `core/runtime-core` and `transports/in-process`;
- observability payloads if identifiers, statuses or errors change;
- serialization and future Binder compatibility;
- tests that pin public behavior.

Do not put backend-specific types into contracts.

### Runtime lifecycle or scheduling change

Start in `core/runtime-core`.

Inspect at minimum:

- `RuntimeOrchestrator.kt`;
- `InferenceBackend.kt`;
- `SingleDecodeScheduler.kt`;
- session/context ownership;
- failure recovery;
- cancellation and memory-pressure paths;
- runtime tests using a fake backend.

State mutations must remain serialized. A failed request must leave the runtime recoverable for a later valid request.

### GGUF storage or integrity change

Start in `models/model-store` and `models/model-profile`.

Preserve:

- streaming I/O for large files;
- atomic staging-to-ready transitions;
- SHA-256 identity;
- duplicate detection;
- active-model deletion protection;
- typed integrity failures.

Never load an entire model into memory merely to import or hash it.

### `llama.cpp`, JNI or native generation change

Start in `backends/llama-cpp`.

Preserve:

- coarse-grained JNI calls;
- opaque, validated and idempotently closeable handles;
- cleanup after partial allocation failures;
- cooperative cancellation during prefill and decode;
- aggregated streaming across JNI;
- typed error mapping at the Kotlin boundary;
- pinned upstream source and reproducible build flags.

Synchronous generation and streaming must share tokenization, context validation, sampler construction, prefill, decode, token conversion, metrics, error mapping and resource ownership. They should differ only in output delivery and cancellation interaction.

Do not include implementation `.cpp` files from other `.cpp` files. Split reusable native responsibilities behind headers and link them through CMake.

### Observability or console change

Start with `observability/contracts` before changing a store or UI.

Metrics and logs must remain privacy-safe by default. Record identifiers, sizes, timings, token counts, statuses and error codes; do not persist prompt or output content unless an explicit diagnostic mode is later designed and visibly enabled.

### New Android or Capacitor integration

Keep product behavior in core modules and make integrations thin adapters.

Do not duplicate model resolution, generation policy, validation, error mapping or telemetry behavior across native Android and Capacitor surfaces.

## Change workflow

1. Inspect the relevant contracts, implementation, tests and documentation before editing.
2. Identify the architectural boundary that owns the behavior.
3. Implement the smallest coherent change without speculative modules or abstractions.
4. Add or update isolated tests for normal, failure, cancellation and lifecycle paths as relevant.
5. Run the narrowest useful checks while iterating.
6. Run the complete validation set before merge.
7. Update the appropriate source of truth in the same change.
8. Keep commits focused and describe behavior, not file movement.

Do not mark a roadmap item complete before its acceptance criteria and required tests are satisfied.

## Validation commands

### Fast Kotlin/JVM checks

```bash
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew check
```

### Android validation

```bash
./gradlew lintDebug :apps:local-llm-console:lintInternal
./gradlew assembleDebug :apps:local-llm-console:assembleInternal
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

### Repository navigation guard

```bash
python3 scripts/verify-agent-navigation.py
```

### Full local gate

Run all commands above. CI in `.github/workflows/validate.yml` is the final reproducible gate, not a replacement for targeted local tests.

Changes touching native loading, generation, memory management or ABI behavior also require an Android `arm64-v8a` device test with a real supported GGUF before they can be considered production-ready.

## Testing expectations

- Put domain logic behind interfaces so it can be tested without loading a real model where practical.
- Use fake backends for orchestration, queueing, error and memory-pressure tests.
- Add native tests for handle registries, metadata parsing, cancellation registries and pure C++ behavior.
- Add real-device tests for JNI linkage, ABI packaging, GGUF compatibility, repeated load/unload, generation, streaming, cancellation and memory stability.
- Test cleanup after failures, not only successful output.
- Test idempotent close/release behavior.
- Avoid assertions that depend on timing unless the test owns a deterministic clock or scheduler.

## Documentation update matrix

| Change | Documentation to update |
| --- | --- |
| Module boundary or dependency direction | `docs/architecture.md`, usually an ADR, and this file if navigation changes |
| New public API or lifecycle behavior | API documentation, `docs/implementation-plan.md` if scope changes, examples |
| Completed or deferred work | `docs/roadmap.md` only; do not duplicate status here |
| New architectural trade-off or irreversible choice | New file under `docs/adr/` and ADR index |
| New validation command or repository guard | This file, `README.md` when contributor-facing, and CI |
| New module | `settings.gradle.kts`, this repository map, tests and relevant architecture docs |
| Feature completion | `docs/definition-of-done.md` checklist plus feature-specific documentation |

## Maintaining `AGENTS.md`

Use the exact uppercase filename `AGENTS.md`. Do not add competing root files such as `agent.md` or `agents.md`.

Keep this file stable and navigational:

- link to canonical documents instead of copying their full content;
- describe durable invariants, ownership boundaries and commands;
- keep current implementation status in `docs/roadmap.md`;
- update the repository map whenever `settings.gradle.kts` changes;
- update task routing when ownership moves between modules;
- update validation commands when CI changes;
- keep links relative so they work in local checkouts and GitHub;
- run `python3 scripts/verify-agent-navigation.py` after every edit.

Add a nested `AGENTS.md` only when a subtree has substantial rules that would otherwise overload this root guide. A nested guide may refine instructions for its subtree but must not contradict root invariants. Link every nested guide from this file so agents can discover it without scanning the repository.

## Stop conditions

Pause and surface the issue rather than improvising when:

- a requested change conflicts with a public contract or accepted ADR;
- documentation and executable behavior disagree materially;
- a model or upstream native dependency would need to be committed directly;
- a change requires exposing backend-native state through public APIs;
- tests reveal unbounded memory growth, use-after-close, data races or unrecoverable runtime state;
- required Android/NDK tooling or a real-device validation step is unavailable.

A partial, explicitly documented result is preferable to claiming a feature is complete without the required evidence.
