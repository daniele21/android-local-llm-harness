# Android Local LLM Harness — Coding Agent Guide

Repository-wide routing and invariants. Keep procedures in Skills and deterministic validation policy in scripts/CI.

## Read only what the task requires

Read this guide, then only:

1. the closest scoped `AGENTS.md`;
2. the owning architecture/feature/workstream source and direct consumers/tests;
3. [`.engineering/commands.json`](.engineering/commands.json) plus [`EXECUTION-CAPABILITY-CONTRACT.md`](EXECUTION-CAPABILITY-CONTRACT.md) for validation/publication work;
4. the relevant Skill under [`skills/`](skills/README.md);
5. for meaningful UX/UI, [`design/ux-contract.json`](design/ux-contract.json), [`design/brand-kit.json`](design/brand-kit.json) and `design-product-experience`.

Canonical routing:

- [`README.md`](README.md) — purpose/modules; [`BRANCHING.md`](BRANCHING.md) — branch/PR policy;
- [`docs/README.md`](docs/README.md) — doc map; [`docs/current-state.md`](docs/current-state.md) — integrated state;
- [`docs/roadmap.md`](docs/roadmap.md), [`docs/implementation-plan.md`](docs/implementation-plan.md) — milestones/target;
- [`docs/architecture.md`](docs/architecture.md), [`docs/adr/README.md`](docs/adr/README.md) — architecture/decisions;
- [`docs/definition-of-done.md`](docs/definition-of-done.md), [`docs/releases/harness-0.5.md`](docs/releases/harness-0.5.md) — completion/release evidence;
- [`.engineering/documentation-policy.json`](.engineering/documentation-policy.json) — policy; [`docs/documentation-policy.json`](docs/documentation-policy.json) is its byte-identical compatibility mirror.

## Repository purpose

Harness answers: **for this use case, device and candidate models, which local model/configuration is the best supported choice?** It owns on-device model lifecycle, inference, evaluation, observability and shared-runtime infrastructure while public/domain contracts remain backend-neutral.

## Non-negotiable invariants

- Public contracts stay independent from Android UI and `llama.cpp`; `core/backend-spi` stays backend-neutral.
- Native pointers/structures never leave the backend boundary; runtime orchestration stays independent from transport/persistence.
- External consumers resolve models through explicit application/use-case control-plane state; no phone-global fallback.
- Catalog selection, verified transfer, installation, activation and residency remain explicit.
- GGUF identity is immutable SHA-256; never commit model binaries.
- Prompts/generated content stay out of normal telemetry, persistence and shared validation reports.
- Cancellation, timeout, shutdown, pressure, partial failure and cleanup are normal lifecycle paths.
- Keep one resident model and one production active decode by default until evidence approves another policy.
- Emulator/CI evidence is never physical-device or production evidence.

## Find the owning boundary

Inspect owner, direct consumers and tests before editing.

| Change | Start here | Inspect next |
| --- | --- | --- |
| Public request/session/error contracts | `core/contracts` | runtime, transports, observability, consumers |
| Backend capabilities | `core/backend-spi` | runtime-core, backends, fakes |
| Runtime/memory/residency | `core/runtime-core` | backend SPI, model store, transports, apps |
| Model profiles/use cases/bindings | `models/model-profile` | resolver, installer, app registries |
| Model store/catalog/download/install | matching `models/*` owner | adjacent lifecycle owner, UI |
| Evaluation | `evaluation/contracts` | engine, adapters, stores, UI |
| llama.cpp/JNI | `backends/llama-cpp` | backend SPI, runtime, device validation |
| Telemetry/resources/benchmarks | matching `observability/*` owner | stores, engines, presenters |
| Shared Binder/control plane | `transports/android-binder-*`, `integrations/android-service-host` | contracts, fixtures |
| Packaged consumer compatibility | `apps/shared-runtime-client-consumer-fixture` | Binder AARs, packaging/R8 |
| Product experience | `design/ux-contract.json`, `ui/design-system` | design Skill, apps, accessibility |
| Connected phone behavior | `apps/local-llm-phone-test` | owning domain contracts |
| CI/packaging/governance | `.github`, `.engineering`, `scripts` | validation/docs |

`settings.gradle.kts` is the canonical Gradle module inventory.

## Product experience routing

Harness adopts `product-ui`. Use `skills/design-product-experience/SKILL.md` at proportional depth: structural UX resolves task/journey/hierarchy before polish; interaction changes cover affected states/recovery/accessibility; visual-only changes preserve settled semantics and stay with the design-system/brand owner.

## Change workflow

1. Confirm canonical owner and smallest coherent scope; resolve material ambiguity from code/contracts/docs/ADRs/consumers/tests.
2. Use `structured-change` for meaningful cross-layer behavior and `plan-workstream` only when persistent coordination is justified.
3. Inspect consumers/fakes/tests before shared-contract changes; implement one coherent vertical slice and update only canonical durable owners.
4. Use `validate-change` while iterating. Diagnose the failure class and owning invariant before editing; repeated failure requires a new hypothesis.
5. Finalize temporary workstreams with `finalize-workstream`.
6. Before publication use `preflight-change`: refresh `dev`, review the full diff, select validation profile, classify execution capability and route unavailable deterministic gates to `remote-preflight` rather than the user.

Ordinary work starts from latest green `dev` and targets `dev`; `main` is promotion/hotfix only under [`BRANCHING.md`](BRANCHING.md).

## Validation profiles

`python3 scripts/detect_ci_scope.py` is canonical; `auto` is the default.

- **LEAN** — docs/governance/metadata and cheap guards; no Android/NDK setup when unnecessary.
- **SCOPED** — contained module plus relevant compile/unit/lint/direct-consumer evidence.
- **STRONG** — runtime/backend/model-store, shared contracts, Binder/control plane, persistence, native/JNI, manifest/dependency/R8/packaging or other cross-boundary/release-sensitive changes.
- **FULL** — promotion/release, selector/CI/global Gradle/module inventory/toolchain changes, unknown executable paths or explicit full request.

`FULL` is exceptional. Stronger validation is allowed; silent downgrade below `auto` is forbidden. If scoped validation misses an impacted deterministic failure, repair the selector/dependency mapping instead of making every PR full.

## Publication readiness

Validation depth and execution location are separate:

- `READY_FOR_CI` — selected deterministic gates ran agent-local and passed;
- `READY_FOR_REMOTE_PREFLIGHT` — required deterministic gates are `REMOTE_AUTOMATED`; the agent triggers `/preflight` instead of asking the user to run Gradle/R8/Lint;
- `AUTOMATED_PREFLIGHT_CONFIRMED` — all deterministic gates selected for the exact head/base passed;
- `NOT_READY_FOR_AUTOMATED_PREFLIGHT` — required evidence, safe scope selection or automation routing is missing/failed.

Physical-device, thermal, memory, representative usability and protected signing evidence is `REAL_ENVIRONMENT`; it may remain `PENDING` after automated preflight but still blocks claims that depend on it.

## Documentation lifecycle

Durable truth belongs in architecture/features/ADRs/tests; `docs/current-state.md` owns current operational state and `docs/workstreams/` temporary active coordination. Transfer durable knowledge and delete completed workstreams by default. Git owns normal implementation history.

## Maintaining agent guides

Keep only durable routing, hazards, invariants and validation selection. Put recurring procedures in Skills and deterministic rules in scripts/CI. Update routing when owners move and rerun guards.

## Stop conditions

Surface conflicts instead of bypassing accepted contracts, material ambiguity, privacy/native/signing/model-safety boundaries, canonical validation/artifact lifecycle, design-system ownership or required real-environment evidence.
