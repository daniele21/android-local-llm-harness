# Harness product-experience overhaul

Status: active
Document type: workstream-state
Owner: apps/local-llm-phone-test
Canonical scope: workstream.phone-product-experience
Read when: coordinating the repo-template-sw 0.5 UX/UI review and implementation of the Harness phone app
Last reviewed: 2026-08-24

Canonical repository status remains in [`docs/current-state.md`](../current-state.md). This temporary file owns sequencing, dependencies, integration and acceptance gates only.

## Integration topology

```text
dev@ae0bc6934d0971e5457b0f4cbc4993f8e51542fd
  └── ux/product-experience-overhaul
        ├── ux/px-10-shell-navigation
        ├── ux/px-20-generation-journey
        ├── ux/px-25-model-lifecycle
        ├── ux/px-30-performance-journey
        └── ux/px-40-diagnostics-settings
```

Feature PRs target `ux/product-experience-overhaul`; one final integration PR targets the then-current `dev`. Historical `ux/harness-*` branches are evidence only because PR #397/#401 already integrated their valid work.

## Goal and decision order

Harness should answer: **for this use case, device and candidate models, which local model/configuration is the best supported choice?**

Use source-backed state and this order:

```text
outcome -> task -> IA/journey -> hierarchy/disclosure/defaults
-> interaction/state/feedback/recovery -> adaptive/platform
-> accessibility -> design system -> motion/polish -> validation
```

PR #397 established the task-first baseline; PR #401 added Performance. This workstream is a completeness/consistency pass, not a brand redesign.

## Priorities

### P0 — correctness and recovery

- Never replace unavailable/not-run with inferred evidence.
- Replace implementation jargon with capability state and next action.
- Preserve semantic severity: failures are errors, not warnings.
- Critical loading/empty/error/disabled/cancelling/partial states need an understandable outcome and recovery when available.
- Labels must describe the exact source-backed quantity/state shown.
- Long operations expose truthful phase/progress and cancellation when supported.

### P1 — hierarchy and disclosure

- Keep current location and primary action obvious.
- Revalidate five-destination compact navigation without weakening scanability/touch behavior.
- Overview stays a decision surface, not a diagnostics duplicate.
- Playground stays `model -> prompt -> preset -> run`; Advanced/Expert remain disclosed progressively.
- Models keeps lifecycle states distinct and makes the next valid action primary.
- Performance reads as dataset -> model -> samples -> profile -> readiness; internal IDs are contextual detail.
- Diagnostics remains summary -> drill-down; raw logs are expert detail.
- Settings remains task-oriented; developer tools stay Advanced.

### P1 — adaptive and accessibility

- Materialize compact/medium/expanded behavior beyond navigation chrome.
- Verify 48 dp targets, TalkBack labels/roles/state descriptions and logical focus.
- Announce important status/error changes accessibly.
- Preserve large-font, landscape and size-change usability.
- Never rely on color alone for critical meaning.

### P2 — convergence, motion and polish

- Extract shared components only for repeated semantic concepts.
- Prefer spacing/grouping before extra cards/borders/badges.
- Motion must communicate feedback, continuity, state, progress or hierarchy and respect reduced motion.
- Use scoped visual regression for stable high-value views only.

## Execution DAG

| ID | State | Depends | Owner | Acceptance |
| --- | --- | --- | --- | --- |
| PX-00 | DONE | — | audit + topology | Current `dev` and 0.5 contract reviewed; integration branch exists; stale UX branches are evidence-only. |
| PX-10 | READY | PX-00 | shell/navigation/adaptive layout | Destination/back behavior stays deterministic; compact nav remains usable; wider layouts preserve task priority. |
| PX-20 | READY | PX-00 | Overview + Playground | One next action; source-backed state; simple default flow; disabled/error/recovery states are clear and accessible. |
| PX-25 | READY | PX-00 | Models lifecycle UI | Lifecycle states remain distinct; failures use error semantics; valid next action/retry/cancel/recovery is explicit. |
| PX-30 | READY | PX-00 | Performance UI | Setup is a bounded decision flow; implementation jargon removed; identity detail is contextual; run/recovery states are complete. |
| PX-40 | READY | PX-00 | Diagnostics + Settings | Severity and labels match source state; diagnostics stays summary -> detail; Advanced remains secondary. |
| PX-50 | BLOCKED | PX-10..40 | shared DS/a11y/adaptive/motion | Repeated semantics converge; 48 dp/focus/announcements/large-font/landscape/expanded/reduced-motion behavior is covered. |
| PX-60 | BLOCKED | PX-50 | E2E/semantics/visual evidence + durable docs | Critical journeys have narrow sufficient evidence; docs match production UI. |
| PX-70 | BLOCKED | PX-60 | final integration | Feature PRs merged; integration reconciled with current `dev`; required gates green; temporary state cleaned. |

## Parallel policy

PX-10/20/25/30/40 own disjoint production files and may run together. Shared design-system abstractions belong to PX-50 after real repetition is visible. Cross-lane dependencies are recorded here instead of editing another lane's owner. Resolve conflicts by replaying the smallest semantic delta on the current integration head.

PX-50 is serialized because it touches shared primitives. PX-60/70 are evidence/integration gates, not new visual scope.

## PR policy

- Each PX-10..40 branch starts from the integration head containing this plan.
- Each lane opens a draft PR to `ux/product-experience-overhaul`, never directly to `dev`.
- Merge only with focused tests and applicable repository/Android gates green on the exact head.
- Review hierarchy/states/recovery, not only appearance.
- Final integration must reconcile newer runtime/evaluation work before targeting `dev`.

## Validation

Per lane:
- presentation/reducer tests for deterministic decisions;
- Compose semantics for primary/disabled/error/loading/empty/navigation states where applicable;
- focused phone-test compile/unit tests and Android Lint;
- DS tests if shared primitives change;
- repository product-experience/documentation/navigation guards.

Final automated evidence:
- representative compact/medium/expanded + portrait/landscape layouts;
- large-font semantics/layout coverage;
- navigation/back/restoration;
- scoped screenshot regression;
- `.engineering/commands.json` gates.

Physical-device evidence remains a separate release claim:
- TalkBack traversal/announcements and large-font/orientation;
- real GGUF install/select/load/generate/stream/cancel/recovery;
- real resource/thermal/evaluation presentation;
- physical validation report/export.

## Durable destinations

On completion, transfer durable knowledge to `design/ux-contract.json` when the contract changes, `docs/harness-ux-ui-implementation-plan.md`, `docs/features/phone-app-architecture.md`, `docs/current-state.md`, and `ui/design-system` / `docs/design-system.md` as applicable. Delete this temporary workstream after PX-70; Git history owns implementation history.