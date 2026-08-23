# Android Local LLM Harness — Coding Agent Guide

This is the repository-wide navigation layer for coding agents. It owns durable invariants, routing and validation selection. It is not a project-status ledger or a substitute for architecture/feature documentation.

## Read only what the task requires

Always read this guide. Then read only:

1. the closest scoped `AGENTS.md` for the target subtree;
2. the canonical architecture, feature or active workstream source required by the task;
3. `.engineering/commands.json` when setup/check/test/E2E/build/package/cleanup behavior matters;
4. the owning implementation, direct consumers, fakes and nearby tests.

Scoped guides currently exist for:

- `models/AGENTS.md` — profiles, catalog, download, installation and model storage;
- `backends/llama-cpp/AGENTS.md` — Kotlin/JNI/C++, CMake, GGUF inspection and upstream pin;
- `observability/AGENTS.md` — telemetry, stores, health, resources and benchmarks;
- `evaluation/AGENTS.md` — dataset/model evaluation contracts, scoring, execution and persistence;
- `apps/local-llm-phone-test/AGENTS.md` — connected Compose app and device evidence.

Use `docs/current-state.md` only when integrated state/blockers matter, `docs/architecture.md` for ownership/dependency questions, accepted `docs/adr/` records for durable decisions, and `docs/README.md` to locate canonical documentation. Do not load every plan for a local change.

## Repository purpose

Android Local LLM Harness answers: **for this use case, device and candidate model set, which local model/configuration is the best supported choice?**

It provides reusable Android-local LLM execution, model lifecycle, evaluation, observability and shared-runtime infrastructure while keeping privacy-sensitive inference on device. `llama.cpp` is the first concrete backend; public/domain contracts remain backend-neutral.

## Non-negotiable invariants

- Public contracts stay independent from Android UI, Capacitor and `llama.cpp` implementation types.
- Backend-neutral execution contracts live in `core/backend-spi`; runtime-core does not depend on concrete `backends:*` implementations.
- Native pointers, ggml/llama structures and backend-owned handles never leave the backend boundary.
- Runtime orchestration stays independent from transport and persistence implementations.
- Model resolution is explicit through application/use-case control-plane state; never silently substitute a phone-global or fallback model for an external consumer.
- Product support is limited to the currently accepted Qwen3.5 dense 0.8B/2B boundary under ADR 0011 while shared contracts remain model-family neutral.
- Catalog selection, verified transfer, installation, binding/activation and runtime residency are separate explicit operations.
- GGUF artifacts use immutable SHA-256 identity; never commit or bundle model binaries.
- Prompts and generated content stay out of normal telemetry, persistence and shared validation reports.
- Cancellation, timeout, shutdown, memory pressure, partial failure and cleanup are normal lifecycle paths.
- Keep one resident model and one production active decode by default until representative evidence approves another policy.
- Emulator/CI evidence must never be presented as physical-device or production evidence.

## Ownership and routing

| Change | Start here | Inspect next |
| --- | --- | --- |
| Public request/session/generation/error contracts | `core/contracts` | runtime, transports, observability, consumers |
| Backend-neutral execution/capability contracts | `core/backend-spi` | runtime-core, concrete backends, fakes |
| Runtime lifecycle/scheduling/memory/residency | `core/runtime-core` | backend SPI, model store, transports, apps |
| Model profiles/use cases/bindings | `models/model-profile` | resolver, installer, app registries |
| GGUF store/import/integrity | `models/model-store` | runtime, installer, model UI |
| Catalog/download/install | matching `models/*` owner | adjacent lifecycle owner and phone UI |
| Model evaluation | `evaluation/contracts` | engine/adapters/store/UI |
| llama.cpp/JNI/native generation | `backends/llama-cpp` | backend SPI, runtime, device validation |
| Telemetry/health/resources/benchmarks | matching `observability/*` owner | stores, engines, presenters |
| Embedded transport | `transports/in-process` | contracts/runtime |
| Shared Binder transport/control plane | `transports/android-binder-*` + `integrations/android-service-host` | shared-runtime docs/contracts/fixtures |
| Shared Compose components | `ui/design-system` | both Android apps and accessibility coverage |
| Connected phone behavior | `apps/local-llm-phone-test` | owning domain contracts |
| CI/packaging/repository governance | `.github`, `.engineering`, `scripts` | affected docs/validation guards |

`settings.gradle.kts` is the authoritative Gradle module inventory. Prefer scoped search and exclude build output and `third_party/llama.cpp` unless upstream integration is the task.

## Project operating commands

Canonical intent-to-command routing lives in `.engineering/commands.json`. Use those commands instead of adding a second repository-wide command vocabulary.

The main intents are `setup`, `doctor`, `check`, `test`, `e2e`, `build`, `package` and `clean`. `dev`, `smoke` and `stop` are explicitly `n/a` until the repository owns a distinct lifecycle for them.

Physical-device E2E requires the model identity inputs declared by the command contract. Missing hardware evidence stays pending; do not weaken the claim.

## Core change workflow

1. Confirm the owning boundary and smallest coherent scope.
2. Use a bounded workstream only when dependencies/state must survive across PRs or agents.
3. Inspect owner, direct consumers, fakes and tests before changing a shared contract.
4. Implement one coherent vertical slice without speculative modules or parallel domain logic.
5. Add deterministic success/invalid-input/failure/cancellation/cleanup coverage where applicable.
6. Run the narrowest sufficient validation while iterating; expand according to blast radius.
7. Update only the canonical durable document whose current behavior or decision changed.
8. Inspect the complete diff before publishing.

Ordinary work starts from the latest green `dev` and targets `dev`. `main` is reserved for validated `dev -> main` promotion or an explicit emergency hotfix as defined in `BRANCHING.md`.

## Documentation lifecycle

- `docs/architecture.md` owns current architecture and ownership.
- `docs/features/` owns durable feature behavior when a dedicated source is justified.
- `docs/adr/` owns accepted durable architectural decisions.
- `docs/current-state.md` is the single repository operational ledger.
- `docs/workstreams/` is reserved for active bounded implementation workstreams.
- Completed workstreams are deleted by default after durable behavior/decisions move to their owners; archive only with independent audit/release/regulatory value.
- Git history owns implementation history.

`.engineering/documentation-policy.json` is the repo-template-sw 0.4 policy surface. `docs/documentation-policy.json` remains a compatibility mirror for the existing Harness documentation validator until that validator is migrated; keep the two files semantically synchronized during the transition.

Agent guides contain only durable routing, hazards, invariants and validation selection. Do not put branch/PR status, completed-work histories, release checklists or duplicate feature specifications here.

## Stop conditions

Surface the conflict instead of improvising when a requested change would violate an accepted ADR/public contract, expose private/native state, commit models/signing material, create a second source of truth, bypass required migration/destructive review, bypass canonical validation/artifact lifecycle, or claim physical/production evidence that was not executed.
