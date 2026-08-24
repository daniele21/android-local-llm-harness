# Harness product-experience overhaul

Status: active
Document type: workstream-state
Owner: apps/local-llm-phone-test
Canonical scope: workstream.phone-product-experience
Read when: coordinating the repo-template-sw 0.5 UX/UI review and implementation of the Harness Android phone application
Last reviewed: 2026-08-25

Canonical repository status remains in [`docs/current-state.md`](../current-state.md). This temporary file owns sequencing, dependencies, integration and acceptance gates only.

## Integration topology

The UX/UI work is isolated from `dev` until the complete integration pass is validated:

```text
dev@ae0bc6934d0971e5457b0f4cbc4993f8e51542fd
  └── integration/ux-ui-template-sw
        ├── ux/px-10-shell-navigation          -> PR #409
        ├── ux/px-20-generation-journey       -> PR #410
        ├── ux/px-25-model-lifecycle           -> PR #411
        ├── ux/px-30-performance-journey       -> PR #412
        └── ux/px-40-diagnostics-settings      -> PR #413
```

All feature PRs target `integration/ux-ui-template-sw`. Only one final integration PR may target the then-current `dev`. Historical `ux/harness-*` and `ux/product-experience-overhaul` branches are evidence only unless an exact missing delta is intentionally replayed.

## Goal and decision order

Harness should answer: **for this use case, device and candidate models, which local model/configuration is the best supported choice?**

Apply the repo-template-sw 0.5 product-experience order without skipping earlier layers for visual polish:

```text
USER OUTCOME
-> TASK MODEL
-> IA / CRITICAL JOURNEY
-> INFORMATION + ACTION HIERARCHY
-> DISCLOSURE / DEFAULTS
-> INTERACTIONS / STATES / FEEDBACK / RECOVERY
-> ADAPTIVE / PLATFORM
-> ACCESSIBILITY
-> DESIGN SYSTEM
-> MOTION
-> VISUAL POLISH / GRAPHICS
-> VALIDATION
```

PR #397 established the task-first baseline and PR #401 added Performance. This workstream is a complete consistency/correctness pass, not a brand redesign.

## Audit priorities

### P0 — correctness, state and recovery

- Never replace unavailable/not-run with inferred evidence.
- Replace implementation jargon with capability state and a useful next action.
- Preserve semantic severity: failures are errors, not warnings.
- Critical loading/empty/error/disabled/cancelling/partial states need understandable feedback and recovery when available.
- Labels must describe the exact source-backed quantity/state shown.
- Long operations expose truthful phase/progress and cancellation when supported.
- Navigation or refresh must never silently execute inference, model mutation or destructive actions.

### P1 — task hierarchy, IA and progressive disclosure

- Current location and primary action must be obvious.
- Overview remains a decision surface rather than a diagnostics duplicate.
- Playground stays `model -> prompt -> preset -> run`; Advanced and Expert remain progressively disclosed.
- Models preserves the distinction between available, downloaded, installed, selected, resident and running states and makes the next valid action primary.
- Performance reads as `dataset -> model -> samples -> execution profile -> readiness`; internal IDs/revisions are contextual detail.
- Diagnostics remains `summary -> drill-down`; raw logs are expert detail.
- Settings remains task-oriented; developer tools stay Advanced.

### P1 — adaptive and accessibility correctness

- Materialize compact/medium/expanded behavior beyond navigation chrome.
- Verify 48 dp interaction targets, TalkBack labels/roles/state descriptions and logical focus order.
- Announce important status/error changes accessibly.
- Preserve large-font, landscape and size-change usability.
- Never rely on color alone for critical meaning.

### P2 — design-system convergence, motion and polish

- Extract shared components only for repeated semantic concepts.
- Prefer spacing and grouping before extra cards/borders/badges.
- Motion must communicate feedback, continuity, spatial relationship, state, progress, attention or hierarchy.
- Reduced-motion behavior and performance take priority over decorative animation.
- Use scoped visual regression only for stable, high-value reference states.

## Execution DAG

| ID | Priority | State | Depends | Exclusive ownership | Acceptance |
| --- | --- | --- | --- | --- | --- |
| PX-00 | P0 | DONE | — | audit + topology | Current `dev` and repo-template-sw 0.5 contract reviewed; dedicated integration branch exists. |
| PX-10 | P1 | IN REVIEW | PX-00 | shell/navigation/adaptive layout | Destination/back behavior stays deterministic; compact navigation remains usable; wider layouts preserve task priority. |
| PX-20 | P0/P1 | IN REVIEW | PX-00 | Overview + Playground | One next action; simple default generation flow; disabled/error/recovery states are clear and accessible. |
| PX-25 | P0/P1 | IN REVIEW | PX-00 | Models lifecycle UI | Lifecycle states remain distinct; failures use error semantics; valid action/retry/cancel/recovery is explicit. |
| PX-30 | P0/P1 | IN REVIEW | PX-00 | Performance UI | Setup is a bounded decision flow; implementation jargon is removed; run/recovery states are complete. |
| PX-40 | P0/P1 | IN REVIEW | PX-00 | Diagnostics + Settings | Severity and labels match source state; diagnostics stays summary -> detail; Advanced remains secondary. |
| PX-50 | P1/P2 | BLOCKED | PX-10..40 | shared DS + a11y + adaptive + motion convergence | Repeated semantics converge; 48 dp/focus/announcements/large-font/landscape/expanded/reduced-motion behavior is covered. |
| PX-60 | P0/P1 | BLOCKED | PX-50 | E2E/semantics/visual evidence + durable docs | Critical journeys have the narrowest sufficient evidence and docs match production UI. |
| PX-70 | P0 | BLOCKED | PX-60 | final integration | Integration reconciled with current `dev`; required gates green; temporary state cleaned; final PR ready for `dev`. |

## Parallel execution policy

PX-10/20/25/30/40 own disjoint production files and run in parallel. Shared design-system abstractions belong to PX-50 only after real repetition is visible. A lane that discovers a cross-lane dependency records it here instead of modifying another lane's owner.

PX-50 is intentionally serialized after surface convergence because it touches shared primitives. PX-60 and PX-70 are evidence/integration gates, not opportunities for additional visual scope.

## PR and merge policy

- PX-10..40 PRs target `integration/ux-ui-template-sw`, never `dev`.
- Each PR must contain only its owned semantic delta and focused evidence; the workstream file is owned by the integration branch.
- Merge a lane only when focused tests and applicable repository/Android gates are green on the exact head.
- Review hierarchy, state, feedback and recovery, not only appearance.
- The final integration PR must reconcile any newer runtime/evaluation work from `dev` before merge.
- Physical-device evidence must never be inferred from host/emulator evidence.

## Validation matrix

Per lane:
- presentation/reducer tests for deterministic decisions;
- Compose semantics for primary/disabled/error/loading/empty/navigation states where applicable;
- focused phone-test compile/unit tests and Android Lint;
- design-system tests if shared primitives change;
- repository product-experience/documentation/navigation guards.

Final automated evidence:
- representative compact/medium/expanded and portrait/landscape layouts;
- large-font semantics/layout coverage;
- navigation/back/restoration;
- scoped screenshot regression for stable reference states;
- `.engineering/commands.json` gates.

Representative physical-device evidence remains a separate release claim:
- TalkBack traversal/announcements and large-font/orientation;
- real GGUF install/select/load/generate/stream/cancel/recovery;
- real resource/thermal/evaluation presentation;
- physical validation report/export.

## Durable destinations

On completion, transfer durable knowledge to `design/ux-contract.json` when the contract changes, `docs/harness-ux-ui-implementation-plan.md`, `docs/features/phone-app-architecture.md`, `docs/current-state.md`, and `ui/design-system` / `docs/design-system.md` as applicable. Delete this temporary workstream after PX-70; Git history owns implementation history.
