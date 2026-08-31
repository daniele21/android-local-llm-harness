# Android Local LLM Harness — Coding Agent Guide

Repository-wide routing and invariants. Keep procedures in Skills, deterministic policy in scripts/CI, and status in [`docs/current-state.md`](docs/current-state.md).

## Read only what the task requires

Read this guide, then only the closest scoped `AGENTS.md`, owning implementation/contracts/tests, and relevant canonical sources:

- [`.engineering/commands.json`](.engineering/commands.json) + [`EXECUTION-CAPABILITY-CONTRACT.md`](EXECUTION-CAPABILITY-CONTRACT.md) for validation/publication;
- `.engineering/e2e.json` + `E2E-ENVIRONMENT-CONTRACT.md` for complete workflows, emulator/device/native/runtime or fidelity claims;
- [`docs/README.md`](docs/README.md) for documentation ownership/README impact;
- the relevant Skill under `skills/`;
- `design/ux-contract.json` + `design/brand-kit.json` for meaningful UX/UI.

Canonical navigation: [`README.md`](README.md) owns identity/public usage; [`BRANCHING.md`](BRANCHING.md) branch/PR policy; [`docs/architecture.md`](docs/architecture.md) architecture; [`docs/adr/README.md`](docs/adr/README.md) decisions; [`docs/roadmap.md`](docs/roadmap.md) and [`docs/implementation-plan.md`](docs/implementation-plan.md) target work; [`docs/definition-of-done.md`](docs/definition-of-done.md) and [`docs/releases/harness-0.5.md`](docs/releases/harness-0.5.md) completion/release evidence. `.engineering/documentation-policy.json` is canonical; [`docs/documentation-policy.json`](docs/documentation-policy.json) is its byte-identical compatibility mirror.

## Repository purpose

Harness answers: **for this use case, device and candidate models, which local model/configuration is the best supported choice?** It owns on-device model lifecycle, inference, evaluation, observability and shared-runtime infrastructure while public/domain contracts remain backend-neutral.

## Non-negotiable invariants

- Public contracts stay independent from Android UI and `llama.cpp`; `core/backend-spi` stays backend-neutral.
- Native pointers/structures never leave the backend boundary; runtime orchestration stays transport/persistence independent.
- External consumers resolve models through explicit application/use-case control-plane state; no phone-global fallback.
- Catalog selection, verified transfer, installation, activation and residency remain explicit; GGUF identity is immutable SHA-256 and model binaries are never committed.
- Prompts/generated content stay out of normal telemetry, persistence and shared validation reports.
- Cancellation, timeout, shutdown, pressure, partial failure and cleanup are normal lifecycle paths.
- Keep one resident model and one production active decode by default until evidence approves another policy.
- Execution capability and environment fidelity are independent. Emulator/CI does not prove production ARM64/native, memory, thermal or OEM behavior.
- Physical validation confirms residual fidelity gaps; automatable whole-system defects should be found earlier.
- Code and affected durable documentation ship together; stale canonical docs block publication.
- README identity and usage are separate owners: preserve valid mission/positioning, but update prerequisites/setup/run/config/public API/UI/examples when the current path changes.

## Find the owning boundary

Inspect owner, direct consumers and tests before editing.

| Change | Start here | Inspect next |
| --- | --- | --- |
| Public request/session/error contracts | `core/contracts` | runtime, transports, observability, consumers |
| Backend capabilities | `core/backend-spi` | runtime-core, backends, fakes |
| Runtime/memory/residency | `core/runtime-core` | SPI, model store, transports, apps |
| Profiles/use cases/bindings | `models/model-profile` | resolver, installer, app registries |
| Model store/catalog/install | matching `models/*` owner | adjacent lifecycle owner, UI |
| Evaluation | `evaluation/contracts` | engine, adapters, stores, UI |
| llama.cpp/JNI | `backends/llama-cpp` | SPI, runtime, device validation |
| Telemetry/resources/benchmarks | matching `observability/*` owner | stores, engines, presenters |
| Binder/control plane | `transports/android-binder-*`, `integrations/android-service-host` | contracts, fixtures |
| Consumer compatibility | `apps/shared-runtime-client-consumer-fixture` | Binder AARs, packaging/R8 |
| Product experience | `design/ux-contract.json`, `ui/design-system` | design Skill/apps/accessibility |
| Connected phone behavior | `apps/local-llm-phone-test` | owning domain contracts |
| E2E fidelity | `.engineering/e2e.json` | owning journey/workflows/tests |
| CI/packaging/governance | `.github`, `.engineering`, `scripts` | validation/docs |
| Documentation impact | `docs/README.md` | affected canonical owner |

`settings.gradle.kts` is the canonical Gradle module inventory.

## Product experience

Harness adopts `product-ui`. Use `skills/design-product-experience/SKILL.md` proportionally: structural UX resolves task/journey/hierarchy before polish; interaction changes cover affected states/recovery/accessibility; visual-only changes preserve settled semantics and design-system/brand ownership.

## Change workflow

1. Confirm canonical owner and smallest coherent scope; resolve material ambiguity from code/contracts/docs/ADRs/consumers/tests.
2. Use `structured-change` for meaningful cross-layer behavior and `plan-workstream` only for persistent coordination.
3. Inspect consumers/fakes/tests before shared contracts; implement one coherent vertical slice.
4. Use `validate-change` while iterating. For complete workflows select the `.engineering/e2e.json` journey and cheapest sufficient fidelity; keep residual physical evidence separate.
5. Diagnose failure class and owning invariant before editing; repeated failure requires a new hypothesis.
6. Assess documentation impact from observable behavior and update only affected durable owners.
7. Finalize temporary workstreams with `finalize-workstream`.
8. Before publication use `preflight-change`: refresh `dev`, review full diff, require `DOCS_CURRENT_WITH_IMPLEMENTATION: PASS`, select validation/fidelity, classify execution capability and route unavailable deterministic gates to `remote-preflight` rather than the user.

Ordinary work starts from latest green `dev` and targets `dev`; `main` is promotion/hotfix only under `BRANCHING.md`.

## Validation profiles

`python3 scripts/detect_ci_scope.py` is canonical; `auto` is the default.

- **LEAN** — docs/governance/metadata and cheap guards;
- **SCOPED** — contained module plus direct compile/unit/lint/consumer evidence;
- **STRONG** — runtime/backend/model-store, shared contracts, Binder/control plane, persistence, native/JNI, manifest/dependency/R8/packaging or cross-boundary/release-sensitive work;
- **FULL** — promotion/release, selector/CI/global Gradle/module inventory/toolchain changes, unknown executable scope or explicit full request.

`FULL` is exceptional. Never silently downgrade below `auto`; if scoped validation misses an impacted deterministic failure, repair the selector/dependency map instead of making every PR full.

## E2E fidelity

`.engineering/e2e.json` is canonical. `binder-contract-serialization` uses `binder-api35-emulator` at `simulated_or_emulated` fidelity and does not prove production ARM64 inference. `local-inference-device-lifecycle` has an explicit automation gap for production ARM64 JNI/`llama.cpp`, real GGUF loading, memory reclamation and thermal/OEM behavior; those remain `REAL_ENVIRONMENT` on `physical-arm64-device` until a truthful automated environment exists.

Executor class (`AGENT_LOCAL`, `REMOTE_AUTOMATED`, `REAL_ENVIRONMENT`) never upgrades fidelity. A real APK on an emulator may prove install/package flow while remaining emulator evidence for hardware claims.

## Publication readiness

Preflight classifies `README_IDENTITY`, `README_USAGE`, `FEATURE_DOCS`, `ARCHITECTURE`, `ADR`, `SECURITY_DATA`, `OPERATIONS`, `PRODUCT_EXPERIENCE`, `CURRENT_STATE` as `UPDATED` or `N/A`.

- `READY_FOR_CI`: docs current and selected agent-local deterministic gates passed.
- `READY_FOR_REMOTE_PREFLIGHT`: semantic/base/diff/docs checks passed and required deterministic gates are remote; trigger repository preflight instead of asking the user to run Gradle/R8/Lint.
- `AUTOMATED_PREFLIGHT_CONFIRMED`: all selected deterministic gates passed on exact head/base at declared automated fidelity.
- `NOT_READY_FOR_AUTOMATED_PREFLIGHT`: stale docs or required scope/gate/fidelity/automation failure remains.

Physical-device, thermal, memory, representative-usability and protected-signing evidence may remain pending after automated preflight but still block dependent claims.

## Documentation ownership

README identity owns stable purpose/audience/outcome/positioning; README usage owns current prerequisites/setup/run/config/public usage/examples. Architecture/features/ADRs/tests and focused runbooks own durable truth; existing feature docs update with the behavior they describe. `docs/current-state.md` owns operational state and `docs/workstreams/` active bounded coordination. Transfer durable knowledge and delete completed workstreams by default; Git owns normal implementation history.

## Maintaining agent guides

Keep only durable routing, hazards, invariants and validation selection. Put recurring procedures in Skills and deterministic rules in scripts/CI; update routing when owners move and rerun guards.

## Stop conditions

Surface conflicts instead of bypassing accepted contracts, material ambiguity, privacy/native/signing/model-safety boundaries, canonical validation/artifact/E2E/documentation lifecycle, design-system ownership or required real-environment evidence.
