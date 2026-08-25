# Harness product-experience realignment

Status: active
Document type: workstream-state
Owner: apps/local-llm-phone-test
Canonical scope: workstream.phone-product-experience
Read when: coordinating the repo-template-sw 0.5 UX/UI realignment of the Harness Android phone application
Last reviewed: 2026-08-25

Canonical repository status remains in [`docs/current-state.md`](../current-state.md); this file owns only temporary implementation sequencing and dependency state for this bounded UX/UI change.

## Goal

Make the connected Harness Android experience answer the product question directly: **for this use case, device and candidate models, which local model/configuration is the best supported choice?**

The UI must preserve expert capability while making the next user decision obvious, using only source-backed state and applying the repo-template-sw 0.5 decision order:

```text
user outcome
-> task model
-> IA / critical journey
-> hierarchy / progressive disclosure / defaults
-> interactions / states / feedback / recovery
-> adaptive / platform
-> accessibility
-> design system
-> motion / polish
-> validation
```

## Integration topology

This workstream uses a bounded integration line so parallel UX/UI work can converge without destabilizing `dev`:

```text
dev
  └── uxui/product-experience-rework
        ├── uxui/uxr-10-overview
        ├── uxui/uxr-20-playground
        ├── uxui/uxr-30-diagnostics
        └── uxui/uxr-40-settings
```

Rules:

- `uxui/product-experience-rework` was created from the exact `dev` baseline and is the temporary merge target for this workstream.
- Workstream PRs target `uxui/product-experience-rework`, not `dev`.
- Parallel branches must have disjoint screen/state ownership. Shared-component changes are deferred to UXR-50 unless a lane cannot proceed without them.
- After UXR-10/20/30/40 converge, UXR-50 and UXR-60 may proceed in parallel where ownership remains disjoint.
- UXR-70 owns the final state/effect convergence after the presentation lanes settle.
- UXR-80 validates the integrated branch. Only after that evidence is green is a single final PR opened from `uxui/product-experience-rework` to `dev`.
- No workstream PR is merged directly to `dev` while this integration line is active.

## Non-goals

- redesign the Harness brand or create a second design system;
- change runtime/model/benchmark policy in the UI layer;
- add cloud fallback or persist prompts/generated output;
- claim physical-device, thermal, performance or usability evidence from host/emulator checks;
- rewrite OMBRA; `apps/local-llm-console` remains a separate consumer product surface.

## Invariants

- `dev` remains the canonical final target; `uxui/product-experience-rework` is the bounded temporary integration target for this workstream.
- Every displayed runtime/model/resource/benchmark/evaluation value is source-backed or explicitly unavailable/not-run.
- Installed, selected, resident and running model states remain distinct.
- Navigation/refresh stays observational; execution requires an explicit action.
- Advanced/expert inference controls remain available but do not dominate the common Playground path.
- Shared visual semantics stay owned by `ui/design-system`; app-specific composition stays in `apps/local-llm-phone-test`.
- Reachable loading/empty/error/disabled/cancelling/recovery states remain explicit.
- Compact/large-font/TalkBack behavior is part of correctness.
- Performance must fail closed rather than fabricate a model/configuration ranking when comparable evidence is unavailable.

## Execution DAG

| ID | State | Depends on | Owns / writes | Can run with | Acceptance |
| --- | --- | --- | --- | --- | --- |
| UXR-00 | DONE | — | `MainActivity.kt`, screen ownership boundaries only | UXR-01 | Overview, Playground and Settings have dedicated owners; Diagnostics retained only the Android effect/composition boundary required for its final migration. |
| UXR-01 | DONE | — | this workstream, `docs/current-state.md`, UX target/progress docs | UXR-00 | Active dependency state is linked from the canonical current-state ledger without creating a second plan/progress pair. |
| UXR-10 | DONE | UXR-00 | Overview screen/presentation + focused tests | UXR-20, UXR-30, UXR-40 | No inferred `Loaded/Warm/Normal` or decorative resource progress; one state-dependent primary next action; explicit unavailable/not-run evidence. |
| UXR-20 | DONE | UXR-00 | Playground screen/presentation + focused tests | UXR-10, UXR-30, UXR-40 | Default path is model -> prompt -> preset -> run; Advanced is collapsed by default; Expert controls are a second disclosure layer; duplicate controls removed; validation/recovery is inline where deterministic. |
| UXR-30 | DONE | UXR-00 | Diagnostics IA/presentation + focused tests | UXR-10, UXR-20, UXR-40 | Diagnostics opens as an evidence overview and drills into Health/Runs/Resources/Benchmarks/Logs/Validation; section changes never execute work; Back behavior is deterministic. |
| UXR-40 | DONE | UXR-00 | Settings screen/presentation + focused tests | UXR-10, UXR-20, UXR-30 | Non-task brand-palette UI removed; Appearance/Privacy/Storage/About/Advanced hierarchy retained; theme state has one render owner and durable persistence. |
| UXR-50 | DONE | UXR-10, UXR-20, UXR-30, UXR-40 | adaptive/accessibility refinements + shared components only when genuinely reusable | UXR-60 | Compact/landscape/medium/expanded and large-font layouts preserve priority; canonical touch targets and non-color-only status semantics are enforced in the connected surfaces. |
| UXR-60 | DONE | UXR-10, UXR-20 | result/evaluation decision layer + evaluation presentation | UXR-50 | Playground quick checks and repeatable Performance evaluation are distinct; History/Compare state explicitly fails closed until compatible source-backed evidence can support a choice. |
| UXR-70 | DONE | UXR-30, UXR-50 | state/effect migration for remaining Diagnostics Activity debt | UXR-60 | Renderable Diagnostics resource state is immutable/ViewModel-owned; Activity remains lifecycle/result/effect root; generation tokens prevent stale Health/resource/benchmark callbacks from overwriting current state. |
| UXR-80 | ACTIVE | UXR-50, UXR-60, UXR-70 | tests/evidence/docs finalization | — | Focused unit/Compose semantics + app compile/lint/package gates pass; representative-device TalkBack/large-font/physical GGUF evidence remains explicitly PENDING until run. |

## Parallel execution policy

`UXR-00` was the intentional serialization point because the original monolithic `MainActivity.kt` created overlapping write ownership. UXR-10/20/30/40 then ran as disjoint presentation lanes. UXR-50 and UXR-60 converged adaptive/accessibility and decision-evidence behavior without sharing primary ownership. UXR-70 serialized the remaining Diagnostics state/effect migration after those surfaces settled.

Do not parallelize two slices that mutate the same screen/state owner. Prefer moving ownership once over resolving repeated merge conflicts. If a shared component becomes necessary for more than one lane, keep lane-specific composition local until a genuinely reusable extraction is clear.

## Current executable slice

- `UXR-80` — validate the exact integrated UX/UI composition, keep the instrumentation semantics contract current, reconcile durable architecture/status documentation and prepare the single final integration PR to `dev`.

Physical TalkBack, representative large-font/landscape behavior and real-GGUF device evidence are not repo-side substitutes for UXR-80 automation; they remain explicit device gates after repository-side validation.

## Validation by slice

- UXR-00: focused phone-test compilation/unit tests; navigation tests; verify no side-effect changes.
- UXR-10/20/30/40: focused presentation/unit tests, Compose semantics, compile + lint.
- UXR-50: compact/landscape/expanded/large-font composition policy plus accessibility semantics; physical TalkBack evidence remains a real-device gate.
- UXR-60: deterministic comparison/presentation tests using source-backed evaluation/telemetry state; no synthetic quality claim is promoted to device evidence.
- UXR-70: reducer/effect race tests, stale-generation invalidation and render-state ownership checks.
- UXR-80: repository product-experience checks, targeted phone-app unit/Compose source validation, lint, packaging and bounded evidence cleanup.

Canonical repository commands and physical evidence rules remain owned by `.engineering/commands.json`, `skills/validate-change/SKILL.md` and `apps/local-llm-phone-test/AGENTS.md`.

## Durable destinations on completion

Transfer durable behavior/decisions to:

- `design/ux-contract.json` only if the durable product contract changes;
- `docs/harness-ux-ui-implementation-plan.md` for the canonical phone UX target;
- `docs/harness-ux-ui-implementation-progress.md` while that legacy owner remains active;
- `docs/features/phone-app-architecture.md` for durable screen/state ownership;
- [`docs/current-state.md`](../current-state.md) for integrated status/blockers;
- `ui/design-system` and `docs/design-system.md` for genuinely reusable components/tokens.

When UXR-80 is integrated, the exact composite is rebased/reconciled with current `dev`, and the final integration PR is green, durable knowledge must remain in the destinations above and this temporary workstream should be removed by default. Device-only evidence stays in its owning evidence/runbook documents rather than being inferred from CI.
