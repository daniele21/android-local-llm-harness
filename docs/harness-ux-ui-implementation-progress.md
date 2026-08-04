# Harness Android UX/UI implementation progress

**Canonical plan:** `docs/harness-ux-ui-implementation-plan.md`
**Implementation audit:** `docs/harness-ux-ui-implementation-audit.md`
**Implementation branch:** `agent/harness-ux-ui-implementation`
**Pull request:** #40
**Last updated:** 2026-08-04
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
| Compose platform foundation | PARTIAL | Compose stack is added; the planned ADR, full foundation evidence, and green CI are missing. |
| Shared design system | PARTIAL | Theme and core card/button/metric components exist; full component inventory, states, charts, semantics, and accessibility hardening remain. |
| Harness launcher identity | PARTIAL | Product label and in-app identity exist; final adaptive, monochrome, and themed assets remain. |
| Responsive application shell | PARTIAL | Compact navigation, expanded rail, and saved destination state exist; Navigation Compose, detail routes, full back behavior, and responsive validation remain. |
| Overview | PARTIAL | Connected model/runtime and latest Playground metrics exist; resource pressure, recent run, active-operation model, and state tests remain. |
| Playground | PARTIAL | Real GGUF inference, streaming, cancellation, cleanup, and metrics are connected. ViewModel/UDF, settings sheet, smart scrolling, UI tests, and device evidence remain. |
| Models | PARTIAL | Import, current-model display, and removal are connected; explicit verify/details/confirmation and durable multi-model catalog remain. |
| Diagnostics container | VALIDATION | Runtime plus selectable Runs, Health, Resources, Benchmarks, Logs, and Validation sections are connected. Detail routes and complete state/navigation tests remain. |
| Settings and developer tools | PARTIAL | Privacy/build disclosures and validation access exist; appearance, storage, metadata, separate routes, and administration controls remain. |
| Shared runtime ownership | DONE | One process-scoped lazy model store, registry, and runtime orchestrator are shared by Playground and physical validation. |
| Telemetry repository injection | DONE | One bounded process-scoped in-memory repository is injected into the runtime. |
| Diagnostics Health | PARTIAL | Explicit non-destructive checks and worst-status aggregation exist; targeted actions and complete capability states remain. |
| Diagnostics Runs | PARTIAL | Real privacy-safe run cards and linked request timelines exist; dedicated detail route and complete state/navigation tests remain. |
| Diagnostics Resources | VALIDATION | Explicit capture, bounded newest-first history, memory trend summary, low-memory count, thermal states, and snapshot cards are connected; charts and device/accessibility evidence remain. |
| Diagnostics Benchmarks | VALIDATION | Cold/warm baselines, per-key readiness, selective capture, bulk ready-key capture, sample progress, and regression cards are connected; retained multi-capture history remains. |
| Diagnostics Logs | VALIDATION | Privacy-safe filters, copy, request correlation, deterministic timelines, and automatic Logs-section opening from run cards are implemented and await final CI/device evidence. |
| Durable multi-model catalog | PENDING | Current persisted metadata still represents one selected/imported model. |
| ViewModel and UDF migration | PENDING | Callback controllers and Activity-owned mutable state remain migration debt. |
| Compose UI and screenshot tests | PENDING | Unit coverage includes runtime, privacy, health, resources, benchmarks, logs, and timeline mapping. |
| CI and Android build validation | VALIDATION | Repository formatting is applied. A standard run is validating Spotless, Detekt, compilation, tests, Lint, assembly, and arm64 packaging on the connected UI. |
| Physical-device validation | VALIDATION | Required with a real GGUF on representative arm64 Android hardware. |

## Implemented connected capabilities

### UX foundation and shared runtime

The branch adds the Compose stack, shared design-system module, Harness identity, responsive shell, and connected Overview, Playground, Models, Diagnostics, and Settings destinations.

`HarnessRuntimeGraph` owns one app-private `FileSystemModelStore`, selected-model registry, current `RuntimeOrchestrator`, and bounded `InMemoryTelemetryRepository`. Constructing the graph does not load a model. Playground and physical validation resolve the same graph.

### Playground and model lifecycle

Real model import, SHA-256 verification, streaming local inference, cooperative cancellation, terminal cleanup, runtime release, model removal, and physical validation remain connected.

The audit corrected Playground prompt and generation-option state so recomposition no longer resets values. The values remain process-memory-only and are not written to saved instance state or telemetry.

### Diagnostics section navigation

The Diagnostics destination now exposes a horizontally scrollable section selector for Runs, Health, Resources, Benchmarks, Logs, and Validation. Runtime status remains visible above the selected section. Opening a request timeline from a run card switches directly to Logs; leaving Logs clears the selected timeline.

This remains Activity-owned state rather than a Navigation Compose graph. Dedicated detail routes, back-stack behavior, and complete navigation tests remain part of the later ViewModel/UDF migration.

### Diagnostics Health

`HarnessHealthSource` uses the existing `HealthEngine` for selected-model, GGUF-integrity, runtime-state, and telemetry-readability checks. Execution is explicit, off-main-thread, and persisted through the same repository.

### Diagnostics Resources

`HarnessResourceSource` composes `AndroidResourceSnapshotProvider` and `ResourceSnapshotRecorder`. Manual capture records process PSS, native heap, Java heap, available memory, low-memory, and thermal status. Unsupported values remain `Unavailable`.

The connected UI now presents a bounded newest-first history, current/minimum/maximum PSS, a trend only when enough samples exist, low-memory sample count, observed thermal states, and up to ten detailed snapshot cards. Graphical charts and physical-device/accessibility evidence remain open.

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
- Benchmarks remains below `DONE`, because retained multi-capture history and richer visualization remain despite connected readiness and selection.
- Health is `PARTIAL`, because targeted checks and complete capability states remain.

The audit also corrected:

- Playground state recreation during recomposition;
- globally enabled keep-screen-on behavior;
- arbitrary exception-message Toast fallbacks in Playground startup;
- repeated capture of already-recorded benchmark baselines.

## Immediate next block

### Validate connected Diagnostics UI

Status: `VALIDATION`

Required work:

1. pass repository Spotless on the formatter-produced source;
2. pass Detekt and Kotlin/Compose compilation;
3. pass JVM unit tests, including resource history and benchmark readiness;
4. pass Android Lint;
5. assemble the phone-test debug APK and scoped packaging targets;
6. verify `arm64-v8a` packaging and the no-model-artifact guard;
7. update this tracker and PR with exact passing evidence;
8. keep physical-device GGUF validation as a separate required gate.

## Planned sequence after CI validation

1. implement the durable multi-model catalog;
2. migrate Activity-owned state to ViewModel/UDF and Navigation Compose detail routes;
3. complete Settings and developer tools;
4. add Compose UI, screenshot, accessibility, responsive, performance, and physical-device evidence.

## Known technical debt

- `MainActivity` still owns multiple screens and mutable state.
- Controllers still use executors and callbacks rather than ViewModel-owned coroutine state.
- Navigation remains destination-state based rather than a full Navigation Compose graph.
- Resource charts and retained benchmark history remain incomplete.
- The telemetry implementation remains in-memory and is cleared by process death.
- The design-system module does not yet contain the full planned component inventory.
- The implementation was accumulated in one draft PR rather than the canonical sequence of small UX PRs.

## Validation gates before marking the PR ready

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
