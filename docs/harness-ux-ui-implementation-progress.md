# Harness Android UX/UI implementation progress

**Canonical plan:** `docs/harness-ux-ui-implementation-plan.md`
**Implementation audit:** `docs/harness-ux-ui-implementation-audit.md`
**Integrated baseline:** `dev` after merged PR #71
**Active implementation branch:** `agent/models-udf-wiring`
**Active pull request:** PR #72 toward `dev`
**Last updated:** 2026-08-06
**Overall status:** In progress

This document is the living progress tracker for the Harness Android UX/UI implementation plan.

## Status legend

- `DONE`: implementation and block-level acceptance criteria are committed on the active branch.
- `PARTIAL`: meaningful implementation exists, but canonical acceptance criteria are not fully satisfied.
- `NEXT`: immediate implementation target.
- `PENDING`: not started.
- `VALIDATION`: implementation exists but awaits build, CI, emulator, or physical-device evidence.

## Current summary

| Workstream | Status | Notes |
| --- | --- | --- |
| Compose platform foundation | PARTIAL | Compose stack is integrated and validated; the explicit connected-app/UI architecture ADR remains. |
| Shared design system | DONE | PR #61 provides split tokens, dark/light/system themes, shared components, previews, WCAG checks and 48 dp touch-target enforcement. |
| Harness launcher identity | DONE | PR #60 provides repository-owned vector, adaptive, monochrome and fallback launcher assets with packaging verification. |
| Responsive application shell | PARTIAL | Top-level Navigation Compose, compact bottom navigation and expanded rail exist. PR #70 adds typed Settings details, a dedicated request timeline route and detail-aware Back behavior; model details, Activity slimming and responsive validation remain. |
| Overview | PARTIAL | Connected model/runtime and latest Playground metrics exist; resource pressure, recent run, active-operation model, and state tests remain. |
| Playground | VALIDATION | Real GGUF inference, streaming, cancellation, cleanup, metrics and ViewModel UDF are connected. PR #68 adds a pure presentation contract and exhaustive JVM coverage for all seven phases; Compose semantics, settings-sheet polish, smart scrolling, responsive smoke checks and physical-device evidence remain. |
| Models | PARTIAL | Import, download/install, explicit verify, confirmation and protected removal are connected. PR #72 routes these operations through `ModelEffects`, renders the unified inventory and removes Activity state mirrors; model details, deterministic recovery and device evidence remain. |
| Diagnostics container | VALIDATION | Runtime plus selectable Runs, Health, Resources, Benchmarks, Logs, and Validation sections are connected. Detail routes and complete state/navigation tests remain. |
| Settings and developer tools | PARTIAL | PR #70 adds separate Privacy, Storage, Build, Developer tools and Physical validation routes with real app/model state; theme persistence, cleanup controls and complete metadata remain. |
| Shared runtime ownership | DONE | One process-scoped lazy model store, registry, and runtime orchestrator are shared by Playground and physical validation. |
| Telemetry repository injection | DONE | One bounded process-scoped in-memory repository is injected into the runtime. |
| Diagnostics Health | PARTIAL | Explicit non-destructive checks and worst-status aggregation exist; targeted actions and complete capability states remain. |
| Diagnostics Runs | PARTIAL | Real privacy-safe run cards exist and PR #70 moves correlated evidence to a dedicated request timeline route; complete navigation, restoration and emulator evidence remain. |
| Diagnostics Resources | VALIDATION | Explicit capture, bounded newest-first history, memory trend summary, low-memory count, thermal states, and snapshot cards are connected; charts and device/accessibility evidence remain. |
| Diagnostics Benchmarks | VALIDATION | Cold/warm baselines, per-key readiness, selective capture, regression cards and retained history are connected; richer charts, state tests and device evidence remain. |
| Diagnostics Logs | VALIDATION | Privacy-safe filters, copy, request correlation, deterministic timelines, and automatic Logs-section opening from run cards are implemented and await final CI/device evidence. |
| Durable multi-model catalog | PARTIAL | Metadata is persisted per digest and PR #72 connects its unified inventory to real controller snapshots and runtime ownership. `lastUsedAt`, detail recovery, restart UI tests and physical evidence remain. |
| ViewModel and UDF migration | PARTIAL | Playground and Models now render from `HarnessUiState` and cross typed effect boundaries. Diagnostics, Overview and Settings still retain Activity-owned state and effects. |
| Compose UI and screenshot tests | PARTIAL | Initial shell-height and destination-reachability instrumentation exists. PR #68 adds state-derived Playground presentation tests, while Compose semantics, golden, accessibility and responsive matrices remain. |
| CI and Android build validation | VALIDATION | PR #71 is merged into `dev` after full validation `31081158228`. PR #72 passed focused Spotless, Detekt, JVM, Lint, Kotlin compilation and Activity-state guards in run `31082897050`; cumulative PR validation remains. |
| Physical-device validation | VALIDATION | Required with a real GGUF on representative arm64 Android hardware. |

## Implemented connected capabilities

### UX foundation and shared runtime

The `dev` branch contains the Compose stack, shared design-system module, Harness identity, responsive shell, and connected Overview, Playground, Models, Diagnostics, and Settings destinations.

`HarnessRuntimeGraph` owns one app-private `FileSystemModelStore`, selected-model registry, current `RuntimeOrchestrator`, and bounded `InMemoryTelemetryRepository`. Constructing the graph does not load a model. Playground and physical validation resolve the same graph.

### Playground and model lifecycle

Real model import, SHA-256 verification, streaming local inference, cooperative cancellation, terminal cleanup, runtime release, model removal, and physical validation remain connected.

The audit corrected Playground prompt and generation-option state so recomposition no longer resets values. The values remain process-memory-only and are not written to saved instance state or telemetry.

### ViewModel and UDF foundation

PR #66 introduces the first isolated Activity-slimming block:

- one immutable `HarnessUiState` for model, Playground, diagnostics, benchmark, logs, navigation, theme and operation state;
- typed `HarnessUiEvent` transitions;
- a pure `HarnessUiReducer` with no Android or runtime effects;
- `HarnessViewModel` exposing `StateFlow<HarnessUiState>`;
- derived busy and keep-screen-on policies;
- deterministic cleanup of stale removal confirmation and selected request timelines;
- independent tracking of concurrent diagnostics actions;
- JVM tests for the highest-risk transitions;
- an explicit Playground-first migration sequence in `docs/harness-viewmodel-udf-foundation.md`.

PR #67 applies that vertical migration to Playground: Compose collects `StateFlow` lifecycle-aware, prompt, settings, progress, response, and metrics render from `HarnessUiState`, controller callbacks dispatch typed events, and start, cancel, and runtime-release actions cross a testable `PlaygroundEffects` boundary. Android controller resources remain Activity-scoped deliberately so native resources cannot outlive a recreated Activity.

PR #68 extracts a pure `PlaygroundPresentation` contract from `HarnessUiState`. Phase labels and semantic tone, run and stop availability, input enablement, response fallback and metric formatting are no longer recalculated inside the private composables. JVM tests cover `IDLE`, `PREPARING`, `QUEUED`, `GENERATING`, `COMPLETED`, `FAILED` and `CANCELLED`, together with busy state, missing-model behavior, cancellation availability and metric fallbacks. This does not replace Compose semantics, emulator or physical-device validation.

### Unified model inventory foundation

PR #71 introduces a pure product-level projection without changing model operations:

- catalog releases retain stable catalog identity and gain a digest only when installed metadata supplies one;
- externally imported GGUF models remain valid installed selections outside the administrator catalog;
- selected and runtime-loaded ownership are represented independently;
- runtime ownership missing from the inventory and loaded-versus-selected mismatches become explicit degraded states;
- catalog, selection and loaded-ownership events rebuild one immutable `HarnessModelInventoryState`;
- catalog refreshes preserve the last known runtime ownership;
- deterministic tests cover lifecycle mapping, imports, mismatches and reducer convergence.

PR #72 connects this projection to the existing model controllers through an Activity-scoped `ModelEffects` boundary and a ViewModel-owned coordinator. Import, refresh, download, cancellation, installation, installed selection, verification and removal now enter through one typed command surface. The Models screen renders from `HarnessUiState.modelInventory`; Overview, Health, Benchmarks, Validation, Settings and Storage consume the same selected-model state. Activity mirrors for selected model, catalog distribution, removal confirmation and diagnostics selection are removed. Controller, launcher, executor and native runtime ownership remain Activity-scoped deliberately. `models/{digest}` and deterministic recovery actions remain the next vertical slice.

### Typed detail navigation

PR #70 introduces the first detail-route slice without changing runtime ownership:

- a pure route and shell-state contract for top-level and detail destinations;
- separate Privacy, Storage, Build, Developer tools and Physical validation screens;
- a dedicated `runs/{requestId}` destination with URL-safe opaque request identifiers;
- detail-aware top bars and bottom-navigation visibility;
- Settings navigation that preserves the previously selected top-level destination;
- request timeline loading and cleanup tied to destination lifecycle;
- JVM coverage for top-level, detail, fallback and request-ID round-trip behavior.

Model details, complete state restoration, responsive emulator evidence and further Activity slimming remain open.

### Diagnostics section navigation

The Diagnostics destination exposes a horizontally scrollable section selector for Runs, Health, Resources, Benchmarks, Logs, and Validation. Runtime status remains visible above the selected section. Opening a request timeline from a run card or correlated log now navigates to a dedicated detail destination; leaving that destination clears the loaded timeline state.

The app uses Navigation Compose for top-level destinations. PR #70 adds a dedicated opaque request-timeline route and moves Settings disclosures into explicit detail destinations. Diagnostics section and loaded timeline data remain Activity-owned, while complete restoration and emulator back-stack evidence remain part of the ViewModel/UDF migration.

### Diagnostics Health

`HarnessHealthSource` uses the existing `HealthEngine` for selected-model, GGUF-integrity, runtime-state, and telemetry-readability checks. Execution is explicit, off-main-thread, and persisted through the same repository.

### Diagnostics Resources

`HarnessResourceSource` composes `AndroidResourceSnapshotProvider` and `ResourceSnapshotRecorder`. Manual capture records process PSS, native heap, Java heap, available memory, low-memory, and thermal status. Unsupported values remain `Unavailable`.

The connected UI presents a bounded newest-first history, current/minimum/maximum PSS, a trend only when enough samples exist, low-memory sample count, observed thermal states, and up to ten detailed snapshot cards. Graphical charts and physical-device/accessibility evidence remain open.

### Diagnostics Benchmarks

Implemented and connected:

- `HarnessBenchmarkSource` over the existing benchmark engine;
- explicit baseline capture, never automatic during refresh or navigation;
- benchmark keys isolated by application, use case, model digest, and `COLD`/`WARM` load kind;
- discovery of keys from completed real runs and existing baselines;
- per-key baseline and post-baseline sample readiness;
- selective capture for one ready key;
- bulk capture restricted to ready keys without overwriting captured baselines;
- baseline metrics and post-baseline regression evaluation;
- privacy-safe presentation that omits model file names and full digests;
- execution on the shared diagnostics executor.

Retained multi-capture history and richer trend visualization remain open.

### Diagnostics Logs and request timeline

Implemented:

- `HarnessLogSource` with bounded repository reads;
- severity, component, event, request, and safe-field search filters;
- explicit empty, filtered-empty, populated, and source-error states;
- request timeline access from run cards and correlated log entries;
- dedicated request-timeline navigation from run cards and correlated log entries;
- deterministic chronological ordering and run-relative offsets;
- copy of the mapped privacy-safe log representation;
- allowlisted fields, shortened model digests, and omission of unknown fields;
- tests for filtering, timeline ordering, offsets, and prompt/output/path/message exclusion;
- composition documentation in `docs/harness-logs-composition.md`.

## Audit corrections

The implementation audit corrected previous completion claims:

- Runs remains `PARTIAL`: PR #70 supplies dedicated detail navigation, while complete restoration, state and emulator navigation evidence remain.
- Resources remains below `DONE`, because charts and physical/accessibility evidence remain despite connected bounded history.
- Benchmarks remains below `DONE`, because richer visualization, complete state tests and device evidence remain despite connected readiness, selection and retained history.
- Health is `PARTIAL`, because targeted checks and complete capability states remain.

The audit also corrected:

- Playground state recreation during recomposition;
- globally enabled keep-screen-on behavior;
- arbitrary exception-message Toast fallbacks in Playground startup;
- repeated capture of already-recorded benchmark baselines.

The tracker previously still described the rebased UI/tooling candidate as unpublished. That integration was completed by PR #65 and is now part of the `dev` baseline.

## Immediate next block

### Add model details and deterministic recovery

Status: `NEXT`

Completed in PR #72:

1. [x] introduce an Activity-scoped `ModelEffects` boundary;
2. [x] group catalog mutations into typed commands;
3. [x] publish catalog, selection and runtime ownership snapshots to the reducer;
4. [x] render Models from `HarnessUiState.modelInventory`;
5. [x] route import, refresh, download, cancel, install, select, verify and remove through the ViewModel coordinator;
6. [x] preserve Playground runtime release before model replacement or removal;
7. [x] remove Activity-owned model, catalog, confirmation and diagnostics mirrors;
8. [x] add fake-effects tests for commands, busy guards, selection and removal confirmation;
9. [x] pass focused Spotless, Detekt, JVM, Lint, Kotlin compilation and state-removal guards;
10. [ ] pass cumulative PR validation and merge into `dev`.

Next implementation slice:

1. add a URL-safe model-detail route using digest when available and stable catalog identity otherwise;
2. derive one detail presentation for compatibility, integrity, installation, selection and loaded ownership;
3. expose deterministic recovery for runtime/selection mismatch and unknown runtime ownership;
4. keep destructive recovery behind explicit confirmation and runtime release;
5. add route, presentation, reducer and effects tests before connected UI evidence.

## Known technical debt

- `MainActivity` still owns multiple screens and mutable state.
- Playground and Models are wired to ViewModel/UDF; Overview, Diagnostics, Settings and developer tools still retain Activity-owned state and effects.
- Controllers still use executors and callbacks; Playground and Models cross typed effect boundaries, while diagnostics and settings controllers have not migrated.
- Navigation Compose covers top-level destinations plus the first Settings and request-timeline details; model detail, restoration and complete back-stack evidence remain.
- Resource charts and richer benchmark-history visualization remain incomplete.
- The telemetry implementation remains in-memory and is cleared by process death.
- The shared design system is integrated; feature screens still contain some one-off composition and spacing that should move to reusable components when repeated.
- The integrated UX work was accumulated in a larger candidate before PR #65; subsequent migration work should remain split into reviewable vertical slices.

## Validation gates before marking the migration ready

- dependency locks reproducible;
- Spotless, Detekt, tests, and Android Lint pass;
- debug APK assembles;
- release packaging guard behaves correctly;
- no GGUF/GGML artifact is committed;
- Compose compiler and AGP/Kotlin compatibility are confirmed;
- import, generation, cancellation, cleanup, and removal pass on physical arm64 hardware;
- Runs, Health, Resources, Benchmarks, Logs, and timelines render real values on-device;
- cold/warm baselines remain isolated;
- regression readiness uses only post-baseline matching samples;
- telemetry privacy exclusions are verified;
- compact, expanded, landscape, TalkBack, and dynamic-text behavior are validated.

## Documentation maintenance rule

A planned block is not complete unless implementation/tests, this progress tracker, the PR sequence, and relevant architecture documentation are updated in the same cycle. The next-action section must identify exactly one immediate implementation block.
