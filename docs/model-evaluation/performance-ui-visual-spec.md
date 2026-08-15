# Performance UI visual and interaction specification

Status: active
Document type: feature-specification
Owner: model-evaluation
Canonical scope: model-evaluation.performance-ui.visual
Read when: implementing or reviewing Performance Compose surfaces
Last reviewed: 2026-08-15

## Purpose and authority

This specification translates the reviewed Performance mockups into the existing Harness Android UI. The mockups define **information hierarchy and interaction intent**; they do not define a second app shell, theme, token set or component library.

When sources disagree, implementation authority is:

1. frozen model-evaluation contracts;
2. current Harness shell/navigation;
3. `ui/design-system` semantic tokens/components;
4. this specification;
5. raster mockups.

Relevant implementation surfaces are `ui/design-system/`, `HarnessDestination.kt`, `HarnessNavigation.kt`, `HarnessViewModel.kt` and `PerformanceUiContracts.kt`.

## Shell and brand integration

Performance is one Harness developer-console destination. Preserve the existing Harness identity, top app bar, settings entry, window-inset behavior, Material theme and compact/expanded navigation ownership.

Inside Performance, secondary navigation is:

```text
Performance
├── Run
├── Datasets
├── History
└── Compare
```

These are internal sections, not four new app-level destinations. The mockup hamburger, duplicate app bar and phone-frame chrome are not implemented.

U-03 must validate the top-level Performance destination at the repository minimum compact width and supported font scales. If a full compact label does not fit, use a concise visible label only if route identity, screen heading, accessibility label and expanded label remain `Performance`. Never shrink typography below design-system rules to force fit.

## Design-system mapping

Do not copy mockup hex colors, radii, shadows, spacing or type sizes. Compose consumes semantic roles and shared components.

Use:

- theme background for the application background;
- theme primary for active navigation and primary actions;
- Harness success/warning/error status tones for state;
- secondary/status roles for privacy/local-only positive information;
- existing surface/elevated-surface roles for cards;
- on-surface-variant for secondary copy;
- outline for dividers/borders.

Color must never imply a hidden combined winner across quality, speed, RAM and thermal metrics. Directionality is metric-specific.

Use current Harness typography, shapes and spacing. Technical IDs may truncate in summaries but must remain inspectable in details. Do not introduce one-off dimensions to reproduce pixels.

## Common layout and ownership

Every section follows:

```text
existing Harness top app bar
Performance section navigation
scrollable section content
optional primary action
existing app-level navigation
```

Section selection comes from `PerformanceState.selectedSection` and survives normal recreation. Compact portrait is single-column. Expanded widths may use two columns only when reading and TalkBack order remain deterministic.

The target UDF boundary is:

```text
Compose
  -> PerformanceIntent
  -> PerformanceViewModel
  -> PerformanceUiReducer
  -> PerformanceState
       + PerformanceEffect
       + PerformanceCommand

PerformanceCommand
  -> connected adapter/repository
  -> state snapshots/events
  -> PerformanceViewModel
```

Compose owns rendering and intent only. It does not own dataset parsing/install, model resolution, preflight, `EvaluationEngine`, Room, comparison math or telemetry aggregation.

## Run setup

Default Run content when no evaluation is active is ordered:

1. dataset;
2. model;
3. sample size;
4. execution profile;
5. readiness/preflight;
6. `Start evaluation`.

### Dataset

Show display name, immutable version, available case count, built-in/custom source where useful and a selection affordance. General Purpose becomes default only after D-06/GP-11 actually exposes it; UI must not fabricate installation.

### Model

Show only installed product-supported artifacts after U-11 connects. Surface readable model name and quantization; keep exact digest/profile identity in detail/diagnostic context. Missing model routes to the existing Models destination instead of creating a second install flow.

### Samples

Target controls are Smoke 20, Quick 50, Standard 100, Extended 200, All and Custom multiples of 10.

Rules:

- unavailable fixed presets are visibly disabled;
- never silently clamp a requested count;
- `All` remains usable for a valid smaller dataset;
- Custom displays the exact resolved count before Start;
- D-08 ultimately owns availability and membership.

U-02 may enforce only simple frozen count rules to keep fake-driven state deterministic. U-12 replaces that provisional decision with the dataset sampling resolver.

### Execution profile

Show profile name/version and a concise explanation of semantic impact. Deterministic mode communicates reproducibility. Do not show incompatible thinking/non-thinking choices.

### Readiness and Start

Readiness is state, not decoration. Render ready conditions, typed blocked reasons with recovery, loading/unavailable separately from failure, and disable Start while not ready. U-14 replaces setup completeness with R-03 production preflight.

`Start evaluation` emits a command; the composable never calls `EvaluationEngine` directly.

## Active evaluation

When `PerformanceState.activeRun` exists, Run switches from setup to active state. Prioritize dataset/preset identity, model/profile summary, completed/total progress, lifecycle phase, elapsed duration, privacy-safe current case/category, available partial metrics and Cancel.

`EvaluationProgress` is authoritative. A ring/bar must have textual and accessibility equivalents such as `37 of 100`.

The mockup ETA is exploratory only. V1 must not display ETA until a domain-owned validated estimator exists.

Never synthesize `0` for missing telemetry. Render measured value, `Unavailable`, or `Not enough samples yet`. Partial quality is explicitly labeled `Partial`.

Navigation away must not cancel a run. `Cancel run` becomes fully connected only with R-09/U-16.

## Completed result

Result header identifies terminal state, dataset/version/sample identity, model/profile, execution profile and duration where available.

Results remain visibly separated:

### Quality

Show aggregate score, category scores, supporting attempted/scored counts and exact Harness subset/dataset version. Bars are optional; numeric values are authoritative and accessible.

### Runtime

Show only available model-preparation, TTFT, latency and throughput metrics, including supporting sample counts where needed.

### Resources

Show available memory and thermal observations. Missing telemetry is `Unavailable`, never `0 MB`.

### Reliability

Show typed completed/scored, invalid-output, timeout, runtime-failure and cancelled/skipped counts/rates.

Actions may include Compare, View case outcomes and Run again. `Run again` creates a new run identity and never mutates history.

## Compare

Compare is compatibility-first. Identify Run A and Run B before deltas. The comparison service, not Compose, determines compatibility.

Independently show quality compatibility, runtime compatibility and exact mismatch reasons.

For compatible dimensions, typed deltas may include aggregate/category quality, reliability, TTFT, latency, throughput and resources. Direction is metric-specific: positive quality can be good while positive memory can be bad.

For an incompatible dimension, suppress calculated deltas and keep raw summaries inspectable. Never replace unavailable/incompatible delta with zero.

## Datasets and import

### Built-in

Show immutable identity/version, case count, source/attribution summary and installed/available state. Do not show destructive delete unless dataset delivery policy explicitly allows it.

### Custom

Show local identity, version, case count, import source, validation state and delete only where D-11 allows it. Custom content remains app-private and outside ordinary telemetry.

### Import

`Import dataset` emits the Android document-picker effect; Compose does not parse files.

```text
idle -> picking -> validating -> installing -> installed
                                      \-> typed failure
```

Errors may include line/case coordinate and typed schema reason but must not echo prompt, expected answer or generated content into logs/snackbars.

## Non-happy states

U-04 explicitly covers:

- **Loading:** bounded progress/skeleton without fake values;
- **Empty:** no history, no custom datasets, no second compare run; lead to a useful action;
- **Unavailable:** no supported model, telemetry source unavailable, incompatible comparison; distinct from failure and zero;
- **Failure:** stable summary plus typed recovery; technical details stay privacy-safe.

## Adaptive and accessibility acceptance

Validate compact portrait, compact landscape, expanded width, large font and TalkBack traversal.

Compact uses single-column cards and reachable primary action. Internal section tabs may scroll only if selected state remains obvious. Landscape may place related cards side by side without eliminating scrolling. Expanded width may place selectors beside readiness or Quality beside Runtime/Resources when reading order remains clear.

Requirements:

- actions satisfy repository touch targets;
- section selection uses tab semantics;
- progress has textual/state description;
- icons/color never carry the only status meaning;
- charts/bars have numeric equivalents;
- destructive actions are explicit;
- large fonts reflow instead of clipping;
- technical IDs have meaningful accessibility labels.

## Task mapping

| View / behavior | Owning tasks |
| --- | --- |
| UDF selection/readiness/commands | U-02 |
| app destination + internal navigation | U-03 |
| loading/empty/unavailable/error | U-04 |
| dataset selector | U-10 |
| model selector | U-11 |
| sample controls | U-12 |
| execution profile | U-13 |
| production readiness | U-14 + R-03 |
| active progress | U-15 + R-10 |
| cancellation | U-16 + R-09 |
| recreation state | U-17 + P-06 |
| history/results | U-20..U-25 |
| compare | U-30..U-33 + P-08/P-09 |
| import/delete | U-40..U-44 + D-10/D-11 |

## Definition of Done

A Performance task is not complete merely because it resembles the mockup. Completion requires:

```text
existing Harness theme and shell reused
no duplicate domain logic in Compose
state/recreation behavior tested where required
loading/empty/unavailable/error explicit
no synthetic benchmark metrics
no prompt/output leakage
compact + landscape + expanded reviewed
large-font + accessibility reviewed
tests cover state/interaction contract
documentation and ledger updated
```
