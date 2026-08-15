# Performance UI visual and interaction specification

Status: active
Document type: feature-specification
Owner: model-evaluation
Canonical scope: model-evaluation.performance-ui.visual
Read when: implementing or reviewing Performance Compose surfaces
Last reviewed: 2026-08-15

## Purpose

This document translates the reviewed Performance mockups into the existing Harness Android UI. The mockups are **hierarchy and interaction references**, not a new theme or an alternative application shell.

The implementation must look and behave like a native part of `apps/local-llm-phone-test` by reusing the current Harness design system, app shell, navigation conventions, accessibility rules and adaptive layout patterns.

## Sources of truth

In case of disagreement, implementation authority is ordered as follows:

1. frozen model-evaluation domain/state contracts;
2. current Harness app shell and navigation behavior;
3. `ui/design-system` semantic tokens/components;
4. this interaction specification;
5. reviewed mockups as visual intent.

Do not copy color values, radii, typography sizes, shadows or spacing from a raster mockup. Compose must consume semantic theme roles and shared components.

Relevant existing implementation surfaces include:

```text
ui/design-system/
apps/local-llm-phone-test/.../HarnessDestination.kt
apps/local-llm-phone-test/.../HarnessNavigation.kt
apps/local-llm-phone-test/.../HarnessViewModel.kt
apps/local-llm-phone-test/.../PerformanceUiContracts.kt
```

## App-shell integration

Performance is a Harness developer-console destination, not a separate application inside the application.

Therefore the implementation must preserve:

- the current Harness identity and top app bar;
- the current settings entry;
- the current navigation ownership and back behavior;
- the current Material theme;
- existing window-inset handling;
- the current compact/expanded shell strategy.

The mockup-specific hamburger menu, duplicate `Performance` app bar and phone-frame chrome are not implemented.

Within the Performance destination, the secondary information architecture is:

```text
Performance
├── Run
├── Datasets
├── History
└── Compare
```

`Run / Datasets / History / Compare` are section navigation inside Performance. They must not become four additional app-level destinations.

### Compact app navigation

U-03 must test the additional top-level Performance entry at the repository's minimum supported compact width before freezing the label treatment. Five equal-width destinations are acceptable only if labels retain readable, non-overlapping semantics at supported font scales.

If the full `Performance` label cannot satisfy compact acceptance without shrinking typography below design-system rules, the compact navigation may use a concise visible destination label while preserving:

- route identity `performance`;
- screen heading `Performance`;
- accessibility description `Performance`;
- full `Performance` label on expanded navigation.

Do not solve compact pressure with hardcoded smaller text.

## Brand and design-system mapping

Performance uses the semantic Harness roles already exposed through `MaterialTheme` and shared design-system components.

### Color semantics

Use semantic meaning rather than view-specific hex colors:

- application background -> theme background;
- primary actions and active navigation -> theme primary;
- successful/available/verified states -> Harness success tone;
- privacy/local-only and secondary positive information -> secondary/status semantic roles;
- warnings/timeouts -> warning tone;
- runtime failures/destructive cancellation/import failure -> error tone;
- cards -> existing surface/elevated-surface roles;
- secondary copy -> on-surface-variant;
- borders/dividers -> outline semantic role.

Quality score, throughput, memory and thermal values must never be colored to imply one universal combined winner. Color communicates state/trend inside a declared metric, not a hidden utility function.

### Typography

Use current Harness typography roles. Metric numbers may receive stronger hierarchy through existing title/display roles, but Performance must not define a parallel type scale.

Long technical identities such as model IDs, dataset versions and run IDs must wrap or ellipsize according to current developer-console conventions. Copyable technical identity must remain available from detail surfaces even when summary cards truncate it.

### Shape and spacing

Use design-system shape and spacing tokens. Do not introduce one-off 13dp/17dp/23dp values merely to reproduce a mockup pixel-for-pixel.

## Common Performance layout

Every Performance section consists of:

```text
existing Harness top app bar
Performance section navigation
scrollable section content
optional sticky/near-bottom primary action
existing app-level navigation
```

Section navigation must preserve selection through configuration changes and derive from `PerformanceState.selectedSection`.

On compact portrait, cards stack vertically. On expanded widths, related cards may form two columns only when reading order remains deterministic and TalkBack order matches visual order.

## View A — Run setup

The Run setup view is the default Performance state when no evaluation is active.

Information hierarchy:

1. dataset selection;
2. model selection;
3. sample size;
4. execution profile;
5. readiness/preflight summary;
6. Start evaluation action.

### Dataset selection card

Show:

- display name;
- immutable version;
- available case count;
- built-in/custom source label where useful;
- selection affordance.

General Purpose is the future built-in default only when it is actually discoverable through the dataset registry. UI must not fabricate a General Purpose installation before D-06/GP-11 provide it.

### Model selection card

Show only a product-supported installed artifact once U-11 is connected. Summary should surface human-readable model name and quantization. Exact digest/profile identity remains available in detail/diagnostic context.

If no model is selected, the existing Models destination is the recovery path. Performance does not create a second download/install workflow.

### Sample selector

Target controls:

- Smoke — 20;
- Quick — 50;
- Standard — 100;
- Extended — 200;
- All;
- Custom multiple of 10.

Rules:

- unavailable presets are visibly disabled;
- never silently clamp a requested preset;
- `All` remains available for a valid dataset smaller than a fixed preset;
- Custom displays its exact resolved count before Start;
- preset availability ultimately comes from D-08, not duplicated UI arithmetic.

During the fake-driven U-02 phase the reducer may enforce only the frozen simple count rules required to keep states deterministic. U-12 replaces that provisional local availability decision with the dataset sampling resolver.

### Execution profile

Show profile name/version and a concise explanation of semantic impact. Deterministic profile should communicate reproducibility; thinking/non-thinking choices must not be displayed when incompatible with the selected model/profile contracts.

### Readiness

Readiness is a first-class state rather than a decorative checklist.

Render:

- ready conditions with positive status treatment;
- typed blocked reasons with actionable recovery;
- unavailable/loading state separately from failure;
- Start disabled while not ready.

U-14 replaces local setup completeness with R-03 production preflight results.

### Primary action

`Start evaluation` is the only primary action in setup. The action emits a command from UDF/ViewModel state; the composable never invokes EvaluationEngine directly.

## View B — Active evaluation

When `PerformanceState.activeRun` is non-null, Run changes from setup to active-run presentation.

Show, in priority order:

- dataset + preset/count identity;
- selected model/profile summary;
- completed / total progress;
- lifecycle phase;
- elapsed duration;
- current case/category only when privacy-safe;
- partial quality/reliability/runtime metrics only when supplied by the connected state;
- cancellation action.

### Progress

The authoritative progress is `EvaluationProgress`. Do not derive attempted/completed numbers from animation state.

A progress ring or linear indicator may be used, but it must have a textual equivalent such as `37 of 100` and an accessibility state description.

### ETA

The reviewed mockup shows an ETA for hierarchy exploration. ETA is **not required for v1** and must not be displayed until a domain-owned, validated estimate exists. The UI must not extrapolate an ETA from one current case.

### Live metrics

Never synthesize `0` for unavailable telemetry. Display one of:

- measured value;
- `Unavailable`;
- `Not enough samples yet` where aggregation requires more observations.

Partial quality must be labeled `Partial` and must not look like the final suite score.

### Cancellation

`Cancel run` is destructive/secondary relative to active progress. Navigation away from Performance must **not** implicitly cancel evaluation. Cancellation becomes fully connected only with R-09/U-16.

## View C — Completed result detail

A completed result is opened from History or immediately after terminal completion.

Header identity includes:

- terminal state;
- dataset + version + sample identity;
- model/profile identity;
- execution profile;
- start/completion duration when available.

Results are always separated into four families.

### Quality

Show:

- aggregate score;
- category scores;
- attempted/scored supporting counts;
- exact Harness subset/dataset version label.

Category bars are optional visualization. Numeric values remain authoritative and accessible.

### Runtime

Show only available metrics, including supporting sample count where aggregation can be partial:

- TTFT median/p95;
- total latency median/p95;
- prefill/decode throughput;
- model preparation metric where available.

### Resources

Show memory/thermal observations with explicit availability. `0 MB` must never mean telemetry missing.

### Reliability

Show typed counts/rates for:

- completed/scored;
- invalid output;
- timeout;
- runtime failure;
- cancelled/skipped.

### Actions

Expected secondary actions:

- Compare;
- View case outcomes;
- Run again where configuration reuse is safe and explicit.

`Run again` creates a new run identity and must not mutate historical results.

## View D — Compare

Compare is compatibility-first.

The top region identifies Run A and Run B before showing deltas. The comparison service, not Compose, determines compatibility.

### Compatibility panel

Independently surface:

- quality compatibility;
- runtime compatibility;
- exact mismatch reasons.

Examples include dataset/sample/evaluator/profile mismatch for quality and device/backend/runtime tuning mismatch for runtime.

### Delta rendering

When compatible, show typed deltas such as:

- aggregate quality;
- category scores;
- reliability;
- TTFT;
- latency;
- throughput;
- resources.

Directionality is metric-specific. `+612 MB` may be visually negative while `+5 quality points` is positive. Do not infer direction from the sign alone.

When a dimension is incompatible, suppress calculated deltas for that dimension and preserve raw run summaries.

The UI must never replace an unavailable/incompatible delta with `0`.

## View E — Datasets

Datasets has two ownership groups:

### Built-in datasets

Show immutable identity, version, case count, source/attribution summary and installed/available status where delivery requires installation.

Built-in packs cannot expose a destructive delete action unless the owning dataset delivery policy explicitly allows removal.

### Custom datasets

Show:

- local display name/identity;
- version;
- case count;
- import source type;
- validation state;
- explicit delete affordance where D-11 allows it.

Custom content remains app-private and is not ordinary telemetry.

### Import

`Import dataset` triggers the Android document picker through a one-shot effect. The composable does not parse files.

Import flow states:

```text
idle
-> picking
-> validating
-> installing
-> installed
or typed failure
```

Failure surfaces may include line/case coordinates and typed schema reason but must not echo sensitive prompt/expected/generated content into logs or snackbar text.

## Loading, empty, unavailable and failure states

U-04 must explicitly cover all section families.

### Loading

Use bounded skeleton/progress treatment without fake values.

### Empty

Examples:

- no evaluation history yet;
- no custom datasets;
- no second compatible run selected.

Empty states should lead to the next useful action rather than look like an error.

### Unavailable

Examples:

- no supported installed model;
- resource telemetry source unavailable;
- runtime comparison unavailable because identities differ.

Unavailable is not failure and not numeric zero.

### Failure

Show a stable user-facing summary plus typed recovery action when known. Technical details belong in privacy-safe diagnostics, not raw dataset/model output.

## Adaptive behavior

Acceptance must cover at least:

- compact portrait;
- compact landscape;
- expanded/large-width device or emulator;
- large font scale;
- TalkBack traversal.

### Compact portrait

Single-column cards. Primary action remains reachable without overlaying content. Section navigation may use scrollable tabs only if all four sections retain clear selected state.

### Landscape

Avoid forcing the entire desktop-like mockup into reduced vertical height. Header/selector cards may use two columns; content remains scrollable.

### Expanded width

Run setup may use a two-column master/detail-like arrangement, for example selectors on one side and readiness/action on the other. Results may place Quality beside Runtime/Resources where reading order stays clear.

No component should depend on a specific screenshot pixel width.

## Accessibility

Every actionable item must satisfy the repository's touch-target/accessibility contract.

Requirements include:

- selected section exposed with tab semantics;
- progress exposed textually and semantically;
- icons never carry the only meaning of success/warning/failure;
- charts/bars have numeric text equivalents;
- destructive actions have explicit labels;
- color is never the sole compatibility/status signal;
- large-font layouts reflow rather than clip metric labels;
- technical IDs use meaningful accessibility labels instead of reading punctuation character-by-character where avoidable.

## UDF and ownership boundary

The target data flow is:

```text
Compose
  -> PerformanceIntent
  -> PerformanceViewModel
  -> PerformanceUiReducer
  -> PerformanceState
       + one-shot PerformanceEffect
       + typed PerformanceCommand

PerformanceCommand
  -> connected adapters/repositories in later tasks
  -> state snapshots/events
  -> PerformanceViewModel
```

Compose owns rendering and user intent only.

It must not own:

- dataset parsing/installation;
- supported-model resolution;
- preflight compatibility;
- EvaluationEngine lifecycle;
- Room persistence;
- comparison compatibility math;
- telemetry aggregation.

## Task mapping

| View / behavior | Owning tasks |
| --- | --- |
| UDF selection/readiness/commands | U-02 |
| app destination + section navigation | U-03 |
| loading/empty/unavailable/error | U-04 |
| dataset selector | U-10 |
| model selector | U-11 |
| sample controls | U-12 |
| execution profile selector | U-13 |
| production preflight/readiness | U-14 + R-03 |
| active progress | U-15 + R-10 |
| cancellation | U-16 + R-09 |
| process recreation state | U-17 + P-06 |
| history/result detail | U-20..U-25 |
| compare | U-30..U-33 + P-08/P-09 |
| custom import/delete | U-40..U-44 + D-10/D-11 |

## Implementation Definition of Done

A Performance UI task is not complete merely because it resembles the reviewed mockup.

Before completion it must demonstrate:

```text
existing Harness theme reused
existing app shell reused
no duplicate domain logic in Compose
state survives normal recreation path where required
loading/empty/unavailable/error states explicit
no synthetic benchmark metrics
no prompt/output leakage
compact + landscape + expanded behavior reviewed
large-font + accessibility behavior reviewed
tests cover state/interaction contract
documentation and task ledger updated
```
