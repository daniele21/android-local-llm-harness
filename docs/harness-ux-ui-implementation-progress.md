# Harness Android UX/UI implementation progress

**Canonical plan:** `docs/harness-ux-ui-implementation-plan.md`
**Implementation audit:** `docs/harness-ux-ui-implementation-audit.md`
**Integrated baseline:** `dev` after merged PR #67
**Active implementation branch:** `agent/playground-presentation-tests`
**Active pull request:** PR #68 toward `dev`
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
| Responsive application shell | PARTIAL | Top-level Navigation Compose, compact bottom navigation and expanded rail exist; detail routes, full back behavior, Activity slimming and responsive validation remain. |
| Overview | PARTIAL | Connected model/runtime and latest Playground metrics exist; resource pressure, recent run, active-operation model, and state tests remain. |
| Playground | VALIDATION | Real GGUF inference, streaming, cancellation, cleanup, metrics and ViewModel UDF are connected. PR #68 adds a pure presentation contract and exhaustive JVM coverage for all seven phases; Compose semantics, settings-sheet polish, smart scrolling, responsive smoke checks and physical-device evidence remain. |
| Models | PARTIAL | Import, download/install, explicit verify, confirmation and protected removal are connected; unified multi-model state, detail routes and degraded-state recovery remain. |
| Diagnostics container | VALIDATION | Runtime plus selectable Runs, Health, Resources, Benchmarks, Logs, and Validation sections are connected. Detail routes and complete state/navigation tests remain. |
| Settings and developer tools | PARTIAL | Session theme selection, privacy/build disclosures, storage summary and validation access exist; preference persistence, full metadata, cleanup and separate routes remain. |
| Shared runtime ownership | DONE | One process-scoped lazy model store, registry, and runtime orchestrator are shared by Playground and physical validation. |
| Telemetry repository injection | DONE | One bounded process-scoped in-memory repository is injected into the runtime. |
| Diagnostics Health | PARTIAL | Explicit non-destructive checks and worst-status aggregation exist; targeted actions and complete capability states remain. |
| Diagnostics Runs | PARTIAL | Real privacy-safe run cards and linked request timelines exist; dedicated detail route and complete state/navigation tests remain. |
| Diagnostics Resources | VALIDATION | Explicit capture, bounded newest-first history, memory trend summary, low-memory count, thermal states, and snapshot cards are connected; charts and device/accessibility evidence remain. |
| Diagnostics Benchmarks | VALIDATION | Cold/warm baselines, per-key readiness, selective capture, regression cards and retained history are connected; richer charts, state tests and device evidence remain. |
| Diagnostics Logs | VALIDATION | Privacy-safe filters, copy, request correlation, deterministic timelines, and automatic Logs-section opening from run cards are implemented and await final CI/device evidence. |
| Durable multi-model catalog | PARTIAL | Metadata is persisted per digest; unified selection/loaded ownership, `lastUsedAt`, degraded-state recovery and restart UI tests remain. |
| ViewModel and UDF migration | PARTIAL | PR #66 provides the shared immutable state and reducer foundation. PR #67 migrates the Playground vertical slice to lifecycle-aware `StateFlow` rendering and a testable effect boundary; Models, Diagnostics, Overview, and Settings remain Activity-owned. |
| Compose UI and screenshot tests | PARTIAL | Initial shell-height and destination-reachability instrumentation exists. PR #68 adds state-derived Playground presentation tests, while Compose semantics, golden, accessibility and responsive matrices remain. |
| CI and Android build validation | VALIDATION | PR #67 is merged into `dev`. PR #68 passed scoped repository, Spotless, Detekt, JVM test, Lint, APK and native-packaging validation in run `31071291383`. |
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

### Diagnostics section navigation

The Diagnostics destination exposes a horizontally scrollable section selector for Runs, Health, Resources, Benchmarks, Logs, and Validation. Runtime status remains visible above the selected section. Opening a request timeline from a run card switches directly to Logs; leaving Logs clears the selected timeline.

The app uses Navigation Compose for top-level destinations, but Diagnostics section and timeline state remain Activity-owned and inline rather than dedicated detail routes. Back-stack behavior and complete navigation tests remain part of the ViewModel/UDF migration.

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
- automatic navigation to the Logs section when a run timeline is opened;
- deterministic chronological ordering and run-relative offsets;
- copy of the mapped privacy-safe log representation;
- allowlisted fields, shortened model digests, and omission of unknown fields;
- tests for filtering, timeline ordering, offsets, and prompt/output/path/message exclusion;
- composition documentation in `docs/harness-logs-composition.md`.

## Audit corrections

The implementation audit corrected previous completion claims:

- Runs is `PARTIAL`, because the canonical plan also requires dedicated detail navigation and complete state tests.
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

### Complete Playground UI evidence

Status: `PRESENTATION CONTRACT VALIDATED / COMPOSE AND DEVICE EVIDENCE NEXT`

Completed across the Playground UDF and presentation slices:

1. [x] collect `HarnessViewModel.uiState` with lifecycle awareness;
2. [x] route `PhonePlaygroundController` callbacks through typed events;
3. [x] introduce `PlaygroundEffects` for snapshot, start, cancel, runtime release and cleanup;
4. [x] render Playground inputs, response, phase and metrics from `HarnessUiState`;
5. [x] route prompt, settings, run and stop intents through `HarnessViewModel`;
6. [x] remove Playground-owned `mutableStateOf` fields from `MainActivity`;
7. [x] add fake-effects JVM tests for attachment, option parsing, busy rejection, start, cancel and release;
8. [x] derive one pure presentation model for labels, tone, controls, fallbacks and metrics;
9. [x] cover idle, preparing, queued, generating, completed, failed and cancelled presentation states with JVM tests;
10. [ ] add Compose semantics/render tests for the connected Playground screen;
11. [ ] validate compact and expanded layouts plus navigation and back behavior on emulators;
12. [x] pass Spotless, Detekt, JVM tests, Lint, APK assembly and packaging verification for PR #68;
13. [ ] preserve real-GGUF physical arm64 validation as a separate release gate.

## Planned sequence after the Playground slice

1. extract detail navigation and continue reducing `MainActivity` to a composition root;
2. migrate Models and unified multi-model state, including degraded-state recovery;
3. migrate Overview, Diagnostics, Settings and developer tools to the same state/effect pattern;
4. complete detail routes and back-stack behavior;
5. add Compose UI, screenshot, accessibility, responsive, performance, and physical-device evidence.

## Known technical debt

- `MainActivity` still owns multiple screens and mutable state.
- The ViewModel/reducer foundation is wired only for Playground; Models, Overview, Diagnostics, Settings and developer tools still use Activity-owned state.
- Controllers still use executors and callbacks; Playground now crosses a typed effect boundary, while the remaining controllers have not yet migrated.
- Navigation Compose covers top-level destinations, but detail routes and complete back-stack tests remain.
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
