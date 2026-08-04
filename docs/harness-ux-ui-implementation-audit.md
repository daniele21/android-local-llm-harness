# Harness Android UX/UI implementation audit

**Canonical plan:** `docs/harness-ux-ui-implementation-plan.md`
**Implementation branch:** `agent/harness-ux-ui-implementation`
**Pull request:** #40
**Audit date:** 2026-08-04

## Audit scope

This audit compares the canonical plan, its acceptance criteria, the current branch diff, the living progress tracker, and the available GitHub Actions evidence.

The audit checks implementation rather than file presence. A workstream is considered complete only when its required behavior, tests, documentation, and validation evidence are present.

## Overall finding

The connected-app direction is correct and the branch contains a meaningful end-to-end vertical slice. The following architectural decisions are implemented coherently:

- the Play-installable application is the connected Harness surface;
- `ComponentActivity` and the modern Activity Result API are used;
- one lazy process-scoped `HarnessRuntimeGraph` owns the model store, binding registry, runtime orchestrator, and telemetry repository;
- Playground and physical validation resolve the same runtime graph;
- the orchestrator receives a real bounded telemetry repository;
- inference, cancellation, cleanup, import, removal, and validation remain connected;
- health, resource capture, and benchmark capture are explicit rather than navigation side effects;
- normal telemetry excludes prompt and generated output content.

The initiative is not complete, and the previous tracker overstated some diagnostic sections. Runs, Resources, and Benchmarks had source-backed cards but did not satisfy all screen-level acceptance criteria from the canonical plan.

## Plan-by-plan assessment

| Planned block | Audited status | Finding |
| --- | --- | --- |
| UX-01 Compose foundation | PARTIAL | Compose stack and `ui:design-system` exist. The planned ADR, complete component foundation, validated dependency mapping documentation, and green CI evidence are missing. |
| UX-02 shell and adaptive navigation | PARTIAL | `ComponentActivity`, modern SAF picker, compact navigation, expanded rail, and Harness identity exist. Full Navigation Compose, detail routes, back-stack behavior, final launcher assets, and inset/accessibility validation are missing. |
| UX-03 Playground | PARTIAL | Real streaming inference, cancellation, bounded output, metrics, and shared runtime are connected. ViewModel/UDF, settings sheet, smart scroll, UI tests, physical-device evidence, and full lifecycle state restoration are missing. |
| UX-04 Models | PARTIAL | Real import, current-model presentation, and removal are connected. Explicit verify action, details route, confirmation dialog, richer import metadata handling, and ViewModel state are missing. |
| UX-05 Overview | PARTIAL | Real selected-model/runtime and latest Playground metrics are shown. Resource pressure, recent telemetry run, active-operation model, richer unavailable states, and acceptance tests are missing. |
| UX-06 observability composition | IMPLEMENTED / VALIDATION | One store/runtime/telemetry composition is implemented and documented. CI and physical generation evidence are still required. Cache controls from the broader console plan are not connected. |
| UX-07 Health and Runs | PARTIAL | Health aggregation and run list exist. Targeted checks, Diagnostics tab state, run detail route, request timeline, and complete empty/error/loading state tests were initially missing. The timeline is added by the audit follow-up but still awaits CI. |
| UX-08 Resources, Benchmarks, Logs | PARTIAL | Explicit resources and benchmarks exist. Resources show only the latest snapshot rather than accessible bounded history/charts. Benchmarks lack a full per-key readiness selection experience. Logs and request timelines are added by the audit follow-up and await CI. |
| UX-09 Settings and developer tools | PARTIAL | Privacy/build disclosures and validation access exist. Theme preference, storage summary, build/runtime metadata, separate developer routes, and destructive-storage controls are missing. |
| UX-10 multi-model catalog | PENDING | The app still persists one selected/imported model. |
| UX-11 hardening | PENDING | Compose UI tests, screenshot tests, accessibility audit, responsive validation, Macrobenchmark, and final assets are not implemented. |

## Correctness findings

### Shared runtime ownership

The controller factory adapters resolve the same process-scoped `HarnessRuntimeGraph`. The graph owns one `FileSystemModelStore`, one selected-model registry, one current `RuntimeOrchestrator`, and one `TelemetryRepository`. Runtime creation remains lazy and model-specific.

This satisfies the core unification requirement, subject to CI and device validation.

### Playground state defect

The initial Compose Playground declared prompt and generation options with plain `mutableStateOf` inside the composable body. Those values could be recreated during recomposition and were also lost when leaving the destination.

The audit follow-up moves these values to process-memory Activity state. They remain outside `Bundle`, saved-state, Room, preferences, and telemetry, so prompt content is not persisted while navigation within the process no longer resets it.

This is an interim correction. The canonical solution remains a `PlaygroundViewModel` with process-memory-only state.

### Keep-screen-on scope

The initial shell enabled `FLAG_KEEP_SCREEN_ON` for the entire Activity lifetime. The audit follow-up scopes it to active physical validation or Playground generation and clears it when those operations finish.

### Error presentation

The initial Playground startup path exposed arbitrary exception messages through Toast fallbacks. The audit follow-up replaces those messages with fixed privacy-safe text. A complete fixed error-code mapping still belongs in the ViewModel/UDF migration.

### Diagnostic completion claims

The previous tracker marked Runs, Resources, and Benchmarks as `DONE` based on connected source cards. Against the canonical plan:

- Runs lacked request timeline/detail behavior;
- Resources lacked bounded history presentation and charts;
- Benchmarks lacked a complete active-key/readiness experience;
- Diagnostics lacked the planned section tabs and detail routes.

The tracker is corrected to use `PARTIAL` until those acceptance criteria are met.

## Audit follow-up changes

The same branch now adds:

- a privacy-safe `HarnessLogSource`;
- level, component, event, request, and safe-field filtering;
- deterministic request timelines linked from run cards and log entries;
- selected log copy using only the mapped safe representation;
- allowlisted structured fields and shortened model digests;
- explicit empty, filtered-empty, populated, and source-error states;
- unit tests for filters, ordering, offsets, and privacy exclusions;
- Playground process-memory state correction;
- operation-scoped keep-screen-on behavior;
- fixed Playground startup error text;
- Logs composition documentation.

## Validation finding

GitHub Actions has progressed beyond the repository-navigation guard and into the Android Gradle validation job. The latest completed failure before this audit was `spotlessKotlinCheck`; compilation, unit tests, Detekt, Lint, and APK assembly had not yet completed.

The current audit follow-up has triggered a new validation run. No implementation block should be promoted to fully validated until that run and any follow-up corrections pass.

## Correct next sequence

1. Stabilize CI: Spotless, compilation, unit tests, Detekt, Lint, and debug APK assembly.
2. Complete the current Diagnostics acceptance gap: section navigation, bounded resource history presentation, and benchmark per-key readiness.
3. Implement the durable multi-model catalog.
4. Migrate Activity-owned state and callbacks to ViewModel/UDF and Navigation Compose detail routes.
5. Complete settings/developer tools and then accessibility, screenshot, responsive, and physical-device validation.

## Audit conclusion

The plan has been implemented partially and in the correct architectural direction, but not fully and not with the small-PR sequence originally specified. The branch should remain draft. The living tracker and PR description must describe connected behavior separately from full plan completion and validation readiness.
