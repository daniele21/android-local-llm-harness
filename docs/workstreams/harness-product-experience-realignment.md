# Harness product-experience overhaul

Status: active
Document type: workstream-state
Owner: apps/local-llm-phone-test
Canonical scope: workstream.phone-product-experience
Read when: coordinating the complete repo-template-sw 0.5 UX/UI review and implementation of the Harness Android phone application
Last reviewed: 2026-08-24

Canonical repository status remains in [`docs/current-state.md`](../current-state.md). This file owns only temporary sequencing, dependencies, branch/PR integration and acceptance gates for this bounded UX/UI overhaul.

## Integration topology

The workstream uses one dedicated integration branch created from `dev`:

```text
dev@ae0bc6934d0971e5457b0f4cbc4993f8e51542fd
  └── ux/product-experience-overhaul
        ├── ux/px-10-shell-navigation
        ├── ux/px-20-generation-journey
        ├── ux/px-25-model-lifecycle
        ├── ux/px-30-performance-journey
        └── ux/px-40-diagnostics-settings
```

Feature PRs target `ux/product-experience-overhaul`, not `dev`. After the parallel lanes and convergence/evidence gates are green, one final PR from `ux/product-experience-overhaul` targets the then-current `dev`.

Do not merge stale historical `ux/harness-*` branches into this line. PR #397 and PR #401 already integrated their valid product-experience work into `dev`; older branches are evidence only unless an exact missing delta is identified and replayed on the current base.

## Goal

Make Harness answer the product question directly: **for this use case, device and candidate models, which local model/configuration is the best supported choice?**

The interface must preserve expert capability while making the next decision obvious, using only source-backed state and the repo-template-sw 0.5 decision order:

```text
user outcome
-> task model
-> IA / critical journey
-> hierarchy / disclosure / defaults
-> interactions / states / feedback / recovery
-> adaptive / platform
-> accessibility
-> design system
-> motion
-> visual polish / graphics
-> validation
```

## Current audit baseline

PR #397 established a materially stronger task-first phone experience and PR #401 added the source-backed Performance destination. The remaining work is therefore not a brand redesign. It is a completeness and consistency pass across the full product experience.

### P0 — correctness, state and recovery

- Preserve source-backed truth everywhere; unavailable/not-run must never be replaced by inferred or decorative values.
- Remove user-facing implementation language such as whether an internal registry or adapter is "connected"; expose the user-visible capability state and next action instead.
- Correct semantic severity mismatches: a real failure/error must not render as a warning tone merely because a warning component is convenient.
- Every critical loading/empty/error/disabled/cancelling/partial state must expose an understandable outcome and recovery/next step when one exists.
- Storage, model lifecycle and evaluation summaries must describe exactly the source-backed quantity/state they represent; labels must not imply a broader total than the data actually contains.
- Long-running model/evaluation/generation operations must expose truthful progress or phase state and remain cancellable where the runtime supports cancellation.

### P1 — task hierarchy, IA and progressive disclosure

- Make route identity obvious without forcing the user to infer the current destination from body content while the app bar shows only the Harness brand.
- Revalidate compact navigation now that Performance is a fifth primary destination; avoid abbreviations or density that weaken scanability or touch/accessibility behavior.
- Keep Overview as a decision surface, not a second diagnostics dashboard.
- Keep Playground default flow `model -> prompt -> preset -> run`; Advanced and Expert controls remain progressively disclosed with clear disabled-state explanations.
- Keep Models centered on lifecycle decisions: available/downloaded/installed/selected/resident/running are distinct and the next valid action is visually primary.
- Make Performance read as a decision workflow rather than a stack of equally weighted technical cards; move internal IDs/revisions to contextual/detail disclosure.
- Keep Diagnostics as summary -> drill-down; raw logs and low-level evidence remain expert detail rather than competing with health/validation outcomes.
- Keep Settings task-oriented; developer tools remain explicitly advanced.

### P1 — adaptive and accessibility correctness

- Materialize the existing compact/medium/expanded policy into layouts that preserve task priority rather than only changing navigation chrome.
- Verify 48 dp minimum interactive targets for custom clickable rows/cards and deterministic TalkBack labels/roles/state descriptions.
- Add accessible status/error announcements where state changes materially affect the next action.
- Preserve logical focus order, large-font readability, landscape behavior and state across size changes where technically applicable.
- Do not communicate critical meaning by color alone; status text/semantics remain authoritative.

### P2 — design-system convergence, motion and polish

- Replace repeated one-off clickable/status row compositions with canonical design-system components only when they represent a real shared concept.
- Use spacing/grouping before adding more cards, borders or badges.
- Motion is allowed only for feedback, continuity, spatial relationship, state transition, progress, attention, hierarchy or meaningful completion.
- Respect reduced-motion behavior and prefer simple/fast transitions over decorative animation.
- Add scoped visual regression only for stable high-value reference states; do not freeze every incidental pixel.

## Execution DAG

| ID | Priority | State | Depends on | Exclusive ownership | Can run with | Acceptance |
| --- | --- | --- | --- | --- | --- | --- |
| PX-00 | P0 | DONE | — | audit + this workstream + integration topology | — | Current `dev` and repo-template-sw 0.5 contract reviewed; integration branch exists; stale historical UX branches classified as evidence-only. |
| PX-10 | P1 | READY | PX-00 | shell/navigation/orientation, `HarnessDestination.kt`, `HarnessNavigation.kt`, `HarnessScreenLayout.kt`, `HarnessAdaptivePolicy.kt`, focused shell tests | PX-20, PX-25, PX-30, PX-40 | Current destination is obvious; compact fifth-destination navigation remains readable/accessible; detail Back/restoration rules stay deterministic; medium/expanded shell preserves context. |
| PX-20 | P0/P1 | READY | PX-00 | Overview + Playground presentation/composition + focused tests | PX-10, PX-25, PX-30, PX-40 | One obvious next action; no inferred evidence; default generation path stays simple; advanced/expert controls explain unavailable/disabled states; run status/error/recovery is actionable and accessible. |
| PX-25 | P0/P1 | READY | PX-00 | Models catalog/cards/detail + lifecycle presentation + focused tests | PX-10, PX-20, PX-30, PX-40 | Lifecycle states remain distinct; one primary valid action per state; failures use error semantics; progress/cancel/retry/remove/recovery are explicit; filters and empty states remain understandable. |
| PX-30 | P0/P1 | READY | PX-00 | Performance Run/Datasets/History/Compare presentation + focused tests | PX-10, PX-20, PX-25, PX-40 | Dataset -> model -> samples -> execution profile -> readiness reads as a bounded decision flow; implementation jargon removed; internal identity detail disclosed contextually; active/terminal evaluation states and recovery are complete. |
| PX-40 | P0/P1 | READY | PX-00 | Diagnostics overview/detail presentation + Settings presentation + focused tests | PX-10, PX-20, PX-25, PX-30 | Fail/error tones are semantically correct; source loading/error/unavailable remains visible; diagnostics stays summary -> drill-down; Settings labels match the exact source-backed quantity/state and Advanced remains secondary. |
| PX-50 | P1/P2 | BLOCKED | PX-10, PX-20, PX-25, PX-30, PX-40 | shared design-system convergence, accessibility/adaptive cross-cutting pass, purposeful motion + cross-surface tests | — | Shared components own repeated semantics; 48 dp/focus/announcements/large-font/landscape/medium/expanded/reduced-motion behavior is proven without reintroducing duplicated screen logic. |
| PX-60 | P0/P1 | BLOCKED | PX-50 | E2E/Compose semantics/visual evidence + durable docs/status transfer | — | Critical model lifecycle/local generation/physical-validation journeys have the narrowest sufficient evidence; screenshot/accessibility evidence is bounded; target/progress/current-state docs match production UI. |
| PX-70 | P0 | BLOCKED | PX-60 | integration branch final validation and PR to current `dev` | — | All feature PRs merged into integration branch, branch rebased/reconciled with current `dev`, repository/product-experience/Android gates green, no unowned temporary workstream state remains. |

## Parallel execution policy

`PX-10`, `PX-20`, `PX-25`, `PX-30` and `PX-40` deliberately own disjoint production files and may run in parallel. They must not introduce shared design-system abstractions merely to solve a local problem; that convergence belongs to `PX-50` after real repetition is visible.

If a lane discovers that it must change another lane's owner, record the dependency here rather than editing across boundaries. Integration conflicts are resolved by replaying the smallest semantic change on the current integration head, never by force-merging stale UX branches.

`PX-50` is intentionally serialized after the surface lanes because accessibility/adaptive/design-system convergence touches shared primitives and would otherwise create unnecessary merge conflicts. `PX-60` and `PX-70` are evidence/integration gates, not opportunities for new visual scope.

## Branch and PR policy

- Every PX-10..PX-40 implementation branch starts from the exact integration head containing this plan.
- Every lane opens a draft PR against `ux/product-experience-overhaul`.
- A lane becomes mergeable only when its focused tests and applicable repository/Android checks are green on the exact head.
- Merge to the integration branch only after review of hierarchy/states/recovery, not just visual output.
- Do not target `dev` directly from the parallel lanes.
- The final integration PR to `dev` must reconcile any newer runtime/evaluation changes first; it must not overwrite source-backed capability work that landed after this integration branch was created.

## Validation matrix

### Automated per-lane

- pure presentation/reducer tests for deterministic hierarchy/state decisions;
- Compose semantics for primary actions, disabled/error/loading/empty states and navigation roles;
- focused phone-test compile/unit tests and Android Lint;
- design-system tests when shared components/tokens are touched;
- repository product-experience/documentation/navigation guards.

### Final automated

- compact/medium/expanded and portrait/landscape representative layout tests;
- large-font semantics/layout coverage;
- critical navigation/back/restoration tests;
- scoped screenshot regression for stable reference states;
- package/build gates required by `.engineering/commands.json`.

### Representative physical-device evidence

Physical evidence remains a separate release claim and must not be fabricated from CI/emulator runs:

- TalkBack traversal and status announcements;
- large-font and orientation behavior;
- real GGUF install/select/load/generate/stream/cancel/recovery;
- real resource/thermal and evaluation presentation;
- physical validation report/export behavior.

## Durable destinations on completion

Transfer durable behavior and decisions to:

- `design/ux-contract.json` only when the durable product contract changes;
- `docs/harness-ux-ui-implementation-plan.md` for the canonical phone UX target;
- `docs/harness-ux-ui-implementation-progress.md` while that legacy owner remains active;
- `docs/features/phone-app-architecture.md` for durable screen/state ownership;
- `docs/current-state.md` for integrated status/blockers;
- `ui/design-system` and `docs/design-system.md` for genuinely reusable components/tokens.

When PX-70 is complete and durable knowledge has moved, delete this temporary workstream by default; Git history owns the implementation history.