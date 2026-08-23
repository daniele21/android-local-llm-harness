# Harness product-experience realignment

Status: active
Document type: workstream-state
Owner: apps/local-llm-phone-test
Canonical scope: workstream.phone-product-experience
Read when: coordinating the repo-template-sw 0.5 UX/UI realignment of the Harness Android phone application
Last reviewed: 2026-08-23

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

## Non-goals

- redesign the Harness brand or create a second design system;
- change runtime/model/benchmark policy in the UI layer;
- add cloud fallback or persist prompts/generated output;
- claim physical-device, thermal, performance or usability evidence from host/emulator checks;
- rewrite OMBRA; `apps/local-llm-console` remains a separate consumer product surface.

## Invariants

- `dev` remains base/target; this work starts from the latest green `dev`.
- Every displayed runtime/model/resource/benchmark value is source-backed or explicitly unavailable/not-run.
- Installed, selected, resident and running model states remain distinct.
- Navigation/refresh stays observational; execution requires an explicit action.
- Advanced/expert inference controls remain available but do not dominate the common Playground path.
- Shared visual semantics stay owned by `ui/design-system`; app-specific composition stays in `apps/local-llm-phone-test`.
- Reachable loading/empty/error/disabled/cancelling/recovery states remain explicit.
- Compact/large-font/TalkBack behavior is part of correctness.

## Execution DAG

| ID | State | Depends on | Owns / writes | Can run with | Acceptance |
| --- | --- | --- | --- | --- | --- |
| UXR-00 | DONE | — | `MainActivity.kt`, screen ownership boundaries only | UXR-01 | Overview, Playground and Settings now have dedicated owners; Diagnostics is the only remaining lane allowed to edit its legacy Activity block, so all four UI lanes can proceed without overlapping screen edits. |
| UXR-01 | DONE | — | this workstream, `docs/current-state.md`, UX target/progress docs | UXR-00 | Active dependency state is linked from the canonical current-state ledger without creating a second plan/progress pair. |
| UXR-10 | ACTIVE | UXR-00 | Overview screen/presentation + focused tests | UXR-20, UXR-30, UXR-40 | No inferred `Loaded/Warm/Normal` or decorative resource progress; one state-dependent primary next action; explicit unavailable/not-run evidence. |
| UXR-20 | ACTIVE | UXR-00 | Playground screen/presentation + focused tests | UXR-10, UXR-30, UXR-40 | Default path is model -> prompt -> preset -> run; Advanced is collapsed by default; Expert controls are a second disclosure layer; duplicate controls removed; validation/recovery is inline where deterministic. |
| UXR-30 | ACTIVE | UXR-00 | Diagnostics IA/presentation + focused tests | UXR-10, UXR-20, UXR-40 | Diagnostics opens as an evidence overview and drills into Health/Runs/Resources/Benchmarks/Logs/Validation; section changes never execute work; Back/restoration deterministic. |
| UXR-40 | ACTIVE | UXR-00 | Settings screen/presentation + focused tests | UXR-10, UXR-20, UXR-30 | Remove non-task brand-palette UI; retain Appearance/Privacy/Storage/About/Advanced; theme state has one ViewModel render owner and survives the declared lifecycle. |
| UXR-50 | BLOCKED | UXR-10, UXR-20, UXR-30, UXR-40 | adaptive/accessibility refinements + shared components only when genuinely reusable | UXR-60 | Compact/landscape/medium/expanded and large-font layouts preserve priority; touch/focus/semantics/contrast are verified; no critical meaning is color-only. |
| UXR-60 | BLOCKED | UXR-10, UXR-20 | result/evaluation decision layer + evaluation adapters/presentation | UXR-50 | Terminal inference/evaluation surfaces answer a user decision with source-backed latency/throughput/memory/quality evidence and link to raw evidence without inventing rankings. |
| UXR-70 | BLOCKED | UXR-30, UXR-50 | state/effect migration for remaining Diagnostics/Settings Activity debt | UXR-60 | Renderable state immutable/ViewModel-owned; Activity remains lifecycle/result/effect root; stale callbacks cannot overwrite terminal state. |
| UXR-80 | BLOCKED | UXR-50, UXR-60, UXR-70 | tests/evidence/docs finalization | — | Focused unit/Compose semantics + app compile/lint/package gates pass; representative-device TalkBack/large-font/physical GGUF evidence remains explicitly PENDING until run. |

## Parallel execution policy

`UXR-00` was the intentional serialization point because the original monolithic `MainActivity.kt` created overlapping write ownership. Overview, Playground and Settings now have dedicated files, while Diagnostics alone owns the remaining legacy Activity diagnostics block. `UXR-10`, `UXR-20`, `UXR-30` and `UXR-40` therefore run concurrently. Later, `UXR-50` and `UXR-60` may overlap after their prerequisites; final state/effect migration and evidence converge through `UXR-70/80`.

Do not parallelize two slices that mutate the same screen/state owner. Prefer moving ownership once over resolving repeated merge conflicts.

## Current executable slices

- `UXR-10` — validate and refine evidence-backed Overview behavior.
- `UXR-20` — validate and refine Basic -> Advanced -> Expert Playground disclosure and recovery.
- `UXR-30` — replace the six-tab diagnostics entry with an evidence overview + drill-down while preserving explicit execution semantics.
- `UXR-40` — validate Settings hierarchy and move remaining preference persistence behind its durable owner.

## Validation by slice

- UXR-00: focused phone-test compilation/unit tests; navigation tests; verify no side-effect changes.
- UXR-10/20/30/40: focused presentation/unit tests, Compose semantics, compile + lint.
- UXR-50: compact/landscape/expanded/large-font instrumentation plus accessibility semantics; physical TalkBack evidence remains a real-device gate.
- UXR-60: deterministic comparison/presentation tests using source-backed evaluation/telemetry data; no synthetic quality claim is promoted to device evidence.
- UXR-70: reducer/effect race/recreation/back-stack tests.
- UXR-80: repository product-experience checks, targeted app validation, packaging and bounded evidence cleanup.

Canonical repository commands and physical evidence rules remain owned by `.engineering/commands.json`, `skills/validate-change/SKILL.md` and `apps/local-llm-phone-test/AGENTS.md`.

## Durable destinations on completion

Transfer durable behavior/decisions to:

- `design/ux-contract.json` only if the durable product contract changes;
- `docs/harness-ux-ui-implementation-plan.md` for the canonical phone UX target;
- `docs/harness-ux-ui-implementation-progress.md` while that legacy owner remains active;
- `docs/features/phone-app-architecture.md` for durable screen/state ownership;
- `docs/current-state.md` for integrated status/blockers;
- `ui/design-system` and `docs/design-system.md` for genuinely reusable components/tokens.

When all slices are integrated and durable knowledge has moved, remove this temporary workstream by default.