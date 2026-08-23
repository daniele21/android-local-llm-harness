# Android Local LLM Harness — Coding Agent Guide

Repository-wide routing, invariants and validation selection for coding agents.

## Read only what the task requires

Read this guide, then only:

1. the closest scoped `AGENTS.md`;
2. the owning architecture, feature or active-workstream source;
3. [`.engineering/commands.json`](.engineering/commands.json) when operational behavior matters;
4. the relevant project-local Skill under [`skills/`](skills/README.md) for recurring ordered procedures;
5. when user-facing behavior or visual semantics change, [`design/ux-contract.json`](design/ux-contract.json), [`design/brand-kit.json`](design/brand-kit.json) and [`skills/design-product-experience/SKILL.md`](skills/design-product-experience/SKILL.md);
6. the implementation, direct consumers, fakes and nearby tests.

Scoped guides: [`models/AGENTS.md`](models/AGENTS.md), [`backends/llama-cpp/AGENTS.md`](backends/llama-cpp/AGENTS.md), [`observability/AGENTS.md`](observability/AGENTS.md), [`evaluation/AGENTS.md`](evaluation/AGENTS.md), [`apps/local-llm-phone-test/AGENTS.md`](apps/local-llm-phone-test/AGENTS.md), [`integrations/android-service-host/AGENTS.md`](integrations/android-service-host/AGENTS.md), [`transports/android-binder-client/AGENTS.md`](transports/android-binder-client/AGENTS.md), [`transports/android-binder-contract/AGENTS.md`](transports/android-binder-contract/AGENTS.md).

Canonical routing:

- [`README.md`](README.md) — purpose and modules;
- [`BRANCHING.md`](BRANCHING.md) — branch/PR policy;
- [`docs/README.md`](docs/README.md) — documentation map;
- [`docs/current-state.md`](docs/current-state.md) — integrated state and blockers;
- [`docs/roadmap.md`](docs/roadmap.md) — capability milestones;
- [`docs/implementation-plan.md`](docs/implementation-plan.md) — repository target routing;
- [`docs/architecture.md`](docs/architecture.md), [`docs/adr/README.md`](docs/adr/README.md) — architecture and decisions;
- [`docs/definition-of-done.md`](docs/definition-of-done.md), [`docs/releases/harness-0.5.md`](docs/releases/harness-0.5.md) — completion/release gates;
- [`.engineering/documentation-policy.json`](.engineering/documentation-policy.json) — documentation policy;
- [`docs/workstreams/README.md`](docs/workstreams/README.md) — active-workstream lifecycle;
- [`skills/README.md`](skills/README.md) — project-local recurring procedures;
- [`design/ux-contract.json`](design/ux-contract.json), [`design/brand-kit.json`](design/brand-kit.json) — product experience and brand/design-system contracts.

[`docs/documentation-policy.json`](docs/documentation-policy.json) is a compatibility mirror kept byte-identical to the `.engineering` owner by `Repository health`.

## Repository purpose

Harness answers: **for this use case, device and candidate models, which local model/configuration is the best supported choice?** It provides on-device model lifecycle, inference, evaluation, observability and shared-runtime infrastructure while public/domain contracts remain backend-neutral.

## Non-negotiable invariants

- Public contracts stay independent from Android UI, Capacitor and `llama.cpp` types.
- `core/backend-spi` stays backend-neutral; runtime-core does not depend on concrete backends.
- Native pointers/structures never leave the backend boundary.
- Runtime orchestration stays independent from transport and persistence.
- External consumers resolve models through explicit application/use-case control-plane state; no phone-global fallback.
- Catalog selection, verified transfer, installation, activation and residency remain explicit operations.
- GGUF artifacts use immutable SHA-256 identity; never commit model binaries.
- Prompts/generated content stay out of normal telemetry, persistence and shared validation reports.
- Cancellation, timeout, shutdown, pressure, partial failure and cleanup are normal lifecycle paths.
- Keep one resident model and one production active decode by default until evidence approves another policy.
- Emulator/CI evidence is never physical-device or production evidence.

## Find the owning boundary

Inspect the owner, direct consumers and tests before editing.

| Change | Start here | Inspect next |
| --- | --- | --- |
| Public request/session/generation/error contracts | `core/contracts` | runtime, transports, observability, consumers |
| Backend execution/capabilities | `core/backend-spi` | runtime-core, backends, fakes |
| Runtime lifecycle/scheduling/memory/residency | `core/runtime-core` | backend SPI, model store, transports, apps |
| Model profiles/use cases/bindings | `models/model-profile` | resolver, installer, app registries |
| GGUF store/import/integrity | `models/model-store` | runtime, installer, model UI |
| Catalog/download/install | matching `models/*` owner | adjacent lifecycle owner, phone UI |
| Model evaluation | `evaluation/contracts` | engine, adapters, store, UI |
| llama.cpp/JNI/native generation | `backends/llama-cpp` | backend SPI, runtime, device validation |
| Telemetry/health/resources/benchmarks | matching `observability/*` owner | stores, engines, presenters |
| Embedded transport | `transports/in-process` | contracts/runtime |
| Shared Binder/control plane | `transports/android-binder-*`, `integrations/android-service-host` | contracts/fixtures |
| Packaged consumer validation | `apps/shared-runtime-client-consumer-fixture` | Binder AARs, packaging |
| Product experience / shared Compose UI | `design/ux-contract.json`, `ui/design-system` | `skills/design-product-experience/SKILL.md`, Android apps, accessibility |
| Connected phone behavior | `apps/local-llm-phone-test` | owning domain contracts |
| CI/packaging/governance | `.github`, `.engineering`, `scripts` | affected validation/docs |

`settings.gradle.kts` is the Gradle module inventory. Exclude build output and `third_party/llama.cpp` unless relevant.

## Product experience routing

Harness adopts `product-ui`. Meaningful UX/UI work follows [`skills/design-product-experience/SKILL.md`](skills/design-product-experience/SKILL.md) at proportional depth.

Decision order:

```text
user outcome
-> task model
-> information architecture / critical journey
-> information + action hierarchy
-> progressive disclosure / defaults
-> interactions / states / feedback / recovery
-> adaptive / platform behavior
-> accessibility
-> design system / components
-> motion
-> visual polish / graphics
-> validation
```

Classify the change first:

- **structural UX** — use the full sequence;
- **interaction** — start from the owning task/journey and cover affected state, feedback, accessibility, adaptive/component and motion layers;
- **visual-only** — preserve settled flow/interaction semantics and stay with the existing design-system/brand owner.

Do not expose internal architecture merely because implementation options exist. Do not create a new component when the design system already owns the semantic role. Do not use motion, graphics or polish to compensate for unresolved task flow, hierarchy or feedback.

## Change workflow

1. Confirm owner and smallest coherent scope.
2. Use [`skills/plan-workstream/SKILL.md`](skills/plan-workstream/SKILL.md) only when persistent dependency/state coordination is justified.
3. Use [`skills/structured-change/SKILL.md`](skills/structured-change/SKILL.md) before and after meaningful behavior/cross-layer changes.
4. For meaningful user-facing changes, use `design-product-experience` before implementation at the appropriate depth.
5. Inspect owner, consumers, fakes and tests before shared-contract changes.
6. Implement one vertical slice; avoid parallel domain logic.
7. Use [`skills/validate-change/SKILL.md`](skills/validate-change/SKILL.md) to select the narrowest sufficient iteration gate and the correct final blast-radius gate.
8. Update only the canonical durable owner.
9. Finalize completed workstreams with [`skills/finalize-workstream/SKILL.md`](skills/finalize-workstream/SKILL.md); transfer durable knowledge and delete the temporary plan by default.
10. Inspect the complete diff before publishing.

Ordinary work starts from latest green `dev` and targets `dev`; `main` is promotion/hotfix only under [`BRANCHING.md`](BRANCHING.md).

## Validation levels

Use the scoped guide, [`skills/validate-change/SKILL.md`](skills/validate-change/SKILL.md) and [`.engineering/commands.json`](.engineering/commands.json).

- **Documentation/navigation:** documentation and agent guards.
- **Targeted:** owning module plus direct consumers.
- **Repository-wide:** shared contracts, Gradle, CI, packaging or multi-domain changes.
- **Product experience:** changed task/journey, hierarchy, states/recovery, accessibility/adaptive behavior, design-system reuse and purposeful motion/graphics semantics.
- **Native:** CMake/CTest for native ownership changes.
- **Physical device:** hardware-, memory-, thermal-, packaged cross-app or representative usability claims that CI cannot prove.

Missing device/usability evidence remains pending; never upgrade synthetic evidence into a stronger claim.

## Documentation lifecycle

- [`docs/architecture.md`](docs/architecture.md), `docs/features/` and `docs/adr/` own durable knowledge.
- [`docs/current-state.md`](docs/current-state.md) is the single repository operational ledger.
- [`docs/workstreams/`](docs/workstreams/README.md) holds active bounded implementation workstreams.
- `design/` owns durable product-experience/brand routing; generated screenshots remain evidence, not default design truth.
- Completed workstreams are deleted after durable transfer; archive only for independent audit/release/regulatory value.
- Git history owns normal implementation history.

## Maintaining agent guides

Keep only durable routing, hazards, invariants and validation selection. Put recurring conditional procedure in project-local Skills and deterministic rules in scripts/CI. When a canonical source moves, update routing and rerun guards.

## Stop conditions

Surface conflicts instead of bypassing accepted contracts, privacy/native boundaries, signing/model safety, canonical validation/artifact lifecycle, product-experience/design-system ownership, destructive-review requirements or physical-evidence gates.
