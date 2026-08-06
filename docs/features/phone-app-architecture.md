# Connected phone application architecture

Status: active
Document type: feature-specification
Owner: apps/local-llm-phone-test
Canonical scope: phone.architecture
Read when: changing phone-app state ownership, effects, navigation, lifecycle or composition boundaries
Last reviewed: 2026-08-06

## Purpose

`apps/local-llm-phone-test` is the first fully connected Harness product surface. It presents local model management, inference, diagnostics and physical-device validation while preserving the runtime, model-store, privacy and lifecycle boundaries owned by the shared modules.

The application must remain a thin composition layer. It may coordinate Android launchers, lifecycle resources and UI effects, but it must not duplicate runtime scheduling, model integrity, catalog validation, prompt planning, telemetry or benchmark policy.

## Composition boundary

`HarnessRuntimeGraph` owns one process-scoped, lazily used composition of:

- the app-private content-addressed `FileSystemModelStore`;
- selected-model registry and application/use-case binding;
- the current `RuntimeOrchestrator`;
- the `LlamaCppInferenceBackend`;
- one bounded telemetry repository used by the connected surfaces.

Constructing the graph must not load a model or start inference. Playground and physical validation resolve the same graph so they cannot accidentally create parallel runtime ownership.

## UI state and effects

`HarnessUiState` is the immutable render state. `HarnessUiEvent` and the pure reducer own deterministic state transitions. `HarnessViewModel` exposes state through `StateFlow` and coordinates user intents through typed effect boundaries.

Current effect boundaries include:

- `PlaygroundEffects` for prepare, generation, cancellation and runtime release;
- `ModelEffects` for import launch, catalog commands, selection, verification and removal;
- Activity-owned Android lifecycle, launchers, executors and native-resource controllers.

A migrated screen must not retain a second Activity-owned copy of the same domain state. Android resources may remain Activity-scoped when they must not outlive a recreated Activity, but renderable state and user-intent coordination belong behind the ViewModel boundary.

Prompts and generated output are process-memory-only. They must not enter `SavedStateHandle`, `Bundle`, preferences, Room, telemetry, structured logs or shared reports.

## Navigation contract

Navigation Compose owns top-level and detail destinations.

Top-level destinations:

- Overview;
- Playground;
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

## Diagnostics

Diagnostics reads real privacy-safe sources for runtime state, runs, health, resources, benchmarks, logs and request timelines. Refresh and navigation are observational unless the user invokes an explicit action.

Health execution, resource capture, benchmark capture, cache repair and physical validation must remain explicit. The UI must show unavailable or not-run states rather than illustrative values.

## Remaining architecture work

- move Overview, Diagnostics and Settings renderable state and effects behind the same UDF boundary;
- reduce `MainActivity` to composition root, Activity Result wiring and lifecycle ownership;
- complete process recreation and back-stack restoration without persisting sensitive content;
- centralize repeated feature composition in the shared design system when a real reusable pattern exists;
- complete compact, expanded, landscape, font-scale and TalkBack evidence;
- validate the connected lifecycle on representative physical hardware with a real GGUF.

## Validation

A vertical application slice uses the narrowest applicable JVM, Compose, Lint and assembly checks while iterating. Shared-contract or multi-domain changes use the repository-wide gate. Emulator evidence does not replace the physical-device release gate.
