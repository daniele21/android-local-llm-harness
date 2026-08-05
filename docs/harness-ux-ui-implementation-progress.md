# Harness Android UX/UI implementation progress

**Canonical plan:** `docs/harness-ux-ui-implementation-plan.md`
**Implementation audit:** `docs/harness-ux-ui-implementation-audit.md`
**Implementation branch:** `codex/harness-0.5-integration-plan`, ribasata su `origin/dev`
**Pull request:** pending for the rebased UI/release-tooling candidate; historical foundation PR #40 is merged
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
| Playground | PARTIAL | Real GGUF inference, streaming, cancellation, cleanup, and metrics are connected. ViewModel/UDF, settings sheet, smart scrolling, UI tests, and device evidence remain. |
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
| ViewModel and UDF migration | PENDING | Callback controllers and Activity-owned mutable state remain migration debt. |
| Compose UI and screenshot tests | PARTIAL | Initial shell-height and destination-reachability instrumentation exists; complete state/golden/accessibility matrix remains. |
| CI and Android build validation | VALIDATION | Post-rebase local Spotless, Detekt, tests, Lint, APK/AAB assembly and packaging are green; remote PR and cumulative `dev` CI remain. |
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

The app now uses Navigation Compose for top-level destinations, but Diagnostics section and
timeline state remain Activity-owned and inline rather than dedicated detail routes. Back-stack
behavior and complete navigation tests remain part of the ViewModel/UDF migration.

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
- Benchmarks remains below `DONE`, because richer visualization, complete state tests and device evidence remain despite connected readiness, selection and retained history.
- Health is `PARTIAL`, because targeted checks and complete capability states remain.

The audit also corrected:

- Playground state recreation during recomposition;
- globally enabled keep-screen-on behavior;
- arbitrary exception-message Toast fallbacks in Playground startup;
- repeated capture of already-recorded benchmark baselines.

## Immediate next block

### Integrate and validate the rebased UI/tooling candidate

Status: `LOCAL VALIDATION COMPLETE / REMOTE VALIDATION PENDING`

Required work:

1. [x] review the complete rebased diff against `origin/dev`;
2. [x] pass Spotless, design-system accessibility tests, Detekt and Kotlin/Compose compilation;
3. [x] pass phone-test JVM tests and Android Lint;
4. [x] assemble debug APK and release AAB, then verify `arm64-v8a` packaging and model-artifact guard;
5. [ ] rerun the compact emulator smoke tests; no emulator was connected during the post-rebase gate;
6. [ ] publish the candidate branch and open a PR toward `dev`;
7. [ ] obtain cumulative `Repository validation` on the exact current commit;
8. [ ] keep physical-device GGUF validation as a separate required release gate.

## Planned sequence after CI validation

1. extract detail navigation and reduce `MainActivity` to a composition root;
2. migrate Activity-owned state to ViewModel/UDF, starting from Playground;
3. complete unified multi-model state and degraded-state recovery;
4. complete Overview, Diagnostics, Settings and developer tools;
5. add Compose UI, screenshot, accessibility, responsive, performance, and physical-device evidence.

## Known technical debt

- `MainActivity` still owns multiple screens and mutable state.
- Controllers still use executors and callbacks rather than ViewModel-owned coroutine state.
- Navigation Compose covers top-level destinations, but detail routes and complete back-stack tests remain.
- Resource charts and richer benchmark-history visualization remain incomplete.
- The telemetry implementation remains in-memory and is cleared by process death.
- The shared design system is integrated; feature screens still contain some one-off composition and spacing that should move to reusable components when repeated.
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
