# Connected phone application architecture

Status: active
Document type: feature-specification
Owner: apps/local-llm-phone-test
Canonical scope: phone.architecture
Read when: changing phone-app state ownership, effects, navigation, lifecycle or composition boundaries
Last reviewed: 2026-08-25

## Purpose

`apps/local-llm-phone-test` is the first fully connected Harness product surface. It presents local model management, inference, evidence-backed performance evaluation, diagnostics and physical-device validation while preserving the runtime, model-store, privacy and lifecycle boundaries owned by the shared modules.

The application must remain a thin composition layer. It may coordinate Android launchers, lifecycle resources and UI effects, but it must not duplicate runtime scheduling, model integrity, catalog validation, prompt planning, telemetry, evaluation or benchmark policy.

## Composition boundary

`HarnessRuntimeGraph` owns one process-scoped, lazily used composition of:

- the app-private content-addressed `FileSystemModelStore`;
- selected-model registry and application/use-case binding;
- the current `RuntimeOrchestrator`;
- the `LlamaCppInferenceBackend`;
- one bounded telemetry repository used by the connected surfaces.

Constructing the graph must not load a model or start inference. Playground, Diagnostics, Performance and physical validation resolve the same process graph or its neutral contracts so they cannot accidentally create parallel runtime ownership.

## UI state and effects

`HarnessUiState` is the immutable render state. `HarnessUiEvent` and the pure reducer own deterministic state transitions. `HarnessViewModel` exposes state through `StateFlow` and coordinates user intents through typed effect boundaries.

Current effect boundaries include:

- `PlaygroundEffects` for prepare, generation, cancellation and runtime release;
- `ModelEffects` for catalog commands, selection, verification, removal and recovery;
- ViewModel-owned diagnostic action generations for Health, resource capture and benchmark capture, so a stale asynchronous completion cannot overwrite a newer or terminal state;
- Activity-owned Android lifecycle, launchers, executors and native-resource controllers.

Diagnostics resource history is renderable ViewModel state. The Activity may read the underlying source during an explicit refresh/effect, but Compose rendering consumes the immutable `HarnessUiState` projection rather than calling the source directly.

A migrated screen must not retain a second Activity-owned copy of the same renderable domain state. Android resources may remain Activity-scoped when they must not outlive a recreated Activity, but renderable state and user-intent coordination belong behind the ViewModel boundary. Activity teardown invalidates outstanding diagnostic action generations before executor/controller teardown.

Prompts and generated output are process-memory-only. They must not enter `SavedStateHandle`, `Bundle`, preferences, Room, telemetry, structured logs or shared reports.

## Navigation contract

Navigation Compose owns top-level and detail destinations.

Top-level destinations:

- Overview;
- Playground;
- Performance;
- Models;
- Diagnostics;
- Settings.

Detail destinations include:

- Settings privacy, storage, build, developer tools and physical validation;
- request timelines keyed by an opaque URL-safe request identifier;
- model details keyed by digest when available or stable catalog identity otherwise.

Opening a route must be side-effect free. Navigation must not prepare a model, start health or benchmark work, capture resources, download a model or cancel active generation.

Prompt text, output, filesystem paths, document URIs, signed URLs and backend exception messages are never route arguments.

Back behavior must be deterministic: detail routes return to their parent, Settings returns to the previously selected top-level destination and active runtime work remains governed by its explicit lifecycle rather than navigation.

## Model inventory

The Models surface consumes the unified immutable projection documented in [`../harness-model-inventory-state.md`](../harness-model-inventory-state.md). Catalog state, installed metadata, external imports, selected identity and runtime-loaded ownership remain separate inputs.

Selection, installation and RAM residency are distinct:

- installed means the verified artifact exists in `ModelStore`;
- selected means the application/use case resolves that identity;
- loaded means the runtime currently owns the model in memory.

Removal must remain protected while runtime ownership or active work prevents safe deletion. Releasing runtime ownership must never delete the installed GGUF.

## Playground lifecycle

The connected Playground performs the real lifecycle through shared contracts:

```text
prepare
create logical session
plan prompt and effective configuration
materialize or reuse a compatible context
generate and stream
complete, fail or cancel
close session
release runtime ownership only when explicitly requested or required
```

UI presentation derives from state rather than reimplementing runtime policy. Terminal states, cleanup failures and cancellation races must be deterministic and covered with fakes at the ViewModel/effect boundary.

## Performance decision surface

Performance owns the repeatable evaluation journey: Run, Datasets, History and Compare. A quick Playground inference and a repeatable evaluation are distinct user tasks.

The decision layer fails closed. It may summarize recorded evidence and comparison availability, but it must not rank a model or configuration until compatible source-backed latency, throughput, memory and quality evidence is connected to that surface. Missing, not-run, loading, unavailable and not-comparable states remain explicit.

## Diagnostics

Diagnostics reads real privacy-safe sources for runtime state, runs, health, resources, benchmarks, logs and request timelines. It opens as an evidence overview and drills into source-backed sections. Refresh and navigation are observational unless the user invokes an explicit action.

Health execution, resource capture, benchmark capture, cache repair and physical validation must remain explicit. The UI must show unavailable or not-run states rather than illustrative values. Asynchronous Health/resource/benchmark completions are generation-guarded by the ViewModel; stale generations are discarded rather than mutating current UI state.

## Remaining architecture work

- demonstrate process recreation and back-stack restoration without persisting sensitive content;
- add explicit product controls for RAM residency only when the warm-idle TTL policy is defined and source-backed;
- centralize repeated feature composition in the shared design system only when a real reusable pattern exists;
- complete compact, expanded, landscape, font-scale, TalkBack and screenshot evidence on the required device/emulator matrix;
- validate the connected lifecycle on representative physical hardware with a real GGUF.

## Validation

A vertical application slice uses the narrowest applicable JVM, Compose, Lint and assembly checks while iterating. Shared-contract or multi-domain changes use the repository-wide gate. The UX/UI closeout requires repository product-experience checks plus focused phone app compile, unit, Lint and packaging validation. Instrumentation source is maintained for the task-first shell, progressive disclosure, deterministic Diagnostics drill-down and evidence-gated Performance decision state.

Emulator evidence does not replace the physical-device release gate. TalkBack, representative large-font behavior, thermal/performance behavior and physical GGUF claims remain pending until captured on the declared representative device/build matrix.
