# Local LLM Console observability foundation

## Scope

`apps/local-llm-console` is an observability, runtime-inspection and explicit diagnostics-control application.

Its primary views cover:

- overview;
- installed models and explicit model management;
- active runtime;
- generation runs;
- structured logs;
- health and sanity results and controls;
- cache health and repair controls;
- memory and thermal trends;
- benchmark baselines.

Generation-run and request-correlated log cards can open a request detail view containing persisted run metrics and a chronological event timeline.

The console reuses contracts from `observability/contracts`, `observability/health-engine`, `core/contracts`, `core/runtime-core` and `models/model-store`. It does not introduce alternate telemetry schemas, model registries, health-check semantics, cache registries or runtime policy.

## Architecture

```text
MainActivity
    ├── ConsoleChartView
    ├── ConsoleModelControl → ModelStore
    ├── AndroidModelImportStager → Storage Access Framework
    ├── ConsoleHealthControl → HealthEngine
    ├── ConsoleCacheControl
    │       ├── CacheHealthProbe
    │       └── CacheMaintenanceControl
    └── presenters
            ├── ConsolePresenter
            ├── ConsoleInventoryPresenter
            ├── ConsoleHealthPresenter
            ├── ConsoleCachePresenter
            └── ConsoleResourceChartPresenter
                    ↓
        ConsoleSnapshot / ConsoleRequestDetail
                    ↑
            ConsoleDataSource
                    ↑
        TelemetryConsoleDataSource
            ├── TelemetryRepository
            ├── ConsoleRuntimeStateProvider
            ├── ConsoleModelInventoryProvider
            ├── ConsoleModelControl.snapshot()
            ├── ConsoleHealthControl.snapshot()
            └── ConsoleCacheControl.snapshot()
```

`ConsoleDataSource` is the application-facing read boundary. Runtime state, model inventory, model-management capabilities, health controls, cache diagnostics and persisted telemetry are loaded independently. Failure in one source does not prevent the remaining sources from rendering.

Runtime state remains behind `ConsoleRuntimeStateProvider`. `LocalLlmRuntimeStateProvider` adapts the public `LocalLlmClient.runtimeSnapshot()` contract and does not depend on `RuntimeOrchestrator`, Room, Binder or backend implementation types.

Model inventory remains behind `ConsoleModelInventoryProvider`. `ModelStoreInventoryProvider` maps `ModelStoreSnapshot` into UI-safe values and deliberately drops `StoredModel.file`, so private filesystem paths never reach the presenter.

`ConsoleModelControl` is a separate mutation boundary over `ModelStore`. Reading inventory or capability state never imports, verifies or removes a model.

`ConsoleHealthControl` is the explicit health-execution boundary. Reading registered-check state does not execute a check, and executing a check does not bypass `HealthEngine` persistence, aggregation or exception isolation.

`ConsoleCacheControl` separates observational `CacheHealthProbe` instances from mutating `CacheMaintenanceControl` capabilities. Reading cache state never repairs or clears a cache.

## Installed-model view and management

The Models view shows:

- source availability;
- aggregate model count and size;
- the active runtime model;
- full model digests;
- model size;
- snapshot integrity state;
- model-management capability and execution state;
- the latest explicit model-operation outcome.

It distinguishes disconnected inventory, connected-empty inventory, inventory failure and model-management failure.

`ModelStore.snapshot()` is observational and does not hash every artifact. An entry with `verified = false` is shown as `Not checked`, not as corrupt. A successful explicit verification is displayed as the latest operation in the current console session; it is not presented as durable inventory state because the current `ModelStore` contract does not persist that result.

### Explicit import

Import begins only after the user selects `Select and import GGUF`. Android opens a Storage Access Framework picker and `AndroidModelImportStager` copies the selected stream into private cache storage while calculating SHA-256. Provider-reported size is checked when available.

The staged file is passed to `ModelStore.import()`, which remains responsible for content-addressed placement, size and digest validation, deduplication and atomic destination handling. The staging file is deleted after success or failure.

Architecture and quantization labels are required by `GgufArtifact` but are not persisted by the current `ModelStore`. Importing therefore does not create a runtime model profile, bind an application/use case, load the model or start inference.

### Explicit verification

Each installed digest can be verified through `ModelStore.verify()`. Results are reduced to privacy-safe success or failure details; actual file paths and backend messages are not shown.

### Explicit removal

Removal requires an Android confirmation dialog. The presenter disables removal when the runtime snapshot identifies the target digest as loaded, and `ModelStoreConsoleModelControl` can enforce the same guard through an injected loaded-model supplier.

Removal does not unload a runtime model or close sessions. Runtime lifecycle changes remain separate explicit operations.

Import, verification and removal run on the console's single-thread diagnostics executor. All model-management actions are disabled while one operation is active. No model mutation occurs during refresh, discovery, navigation or overview rendering.

Detailed behavior is documented in [`model-management-console.md`](model-management-console.md).

## Active-runtime view

The Runtime view shows only values exposed by the public runtime snapshot:

- connection and runtime state;
- backend identity supplied by the adapter;
- loaded model digest;
- active session count;
- queued-request count;
- source identity.

Session descriptors, context parameters and active-request identity are not exposed by the current `RuntimeSnapshot` and are not inferred or fabricated.

## Health and sanity controls

The Health view combines persisted results with explicit execution controls. A connected `ConsoleHealthControl` can expose a run-all action and one targeted action for each registered check ID.

Opening or refreshing the view never starts a check. Health work runs through the single-thread diagnostics executor outside the Android main thread, and actions are disabled while execution is active. Completion reloads the same `TelemetryRepository`, so `HealthEngine` remains the result owner.

The standalone console registers `ModelIntegrityHealthCheck` against its own sandboxed `FileSystemModelStore`. Generation-sanity actions are capability driven and appear only when an embedded runtime provides the corresponding registered checks.

The adapter preserves `HealthEngine` behavior for complete and targeted execution, worst-status aggregation, `NOT_RUN` results for unknown IDs, duration measurement, persistence and individual-check exception isolation.

## Cache health and repair controls

The Caches view reports each registered cache independently, including total, healthy, stale and orphaned entries, health status, repair availability, source state and the latest repair outcome.

A repair action is rendered only when the cache has a registered health probe, an available unhealthy snapshot and a matching `CacheMaintenanceControl`.

Cache repair is explicit, off the Android main thread and disabled while another diagnostics action is active. No mutation occurs during discovery, refresh or overview rendering.

`ModelIntegrityCacheMaintenanceControl` revalidates stale entries through `ModelStore.verify()`, removes orphaned or invalid entries and leaves failed revalidations visible for retry. It does not expose blind cache clearing as repair. Conditional concurrent-map replacement and removal prevent repair from overwriting entries changed after snapshot capture.

The standalone console keeps cache control disconnected because its process does not own an embedded runtime's integrity cache.

## Resource and thermal charts

The Resources view retains persisted snapshot cards and adds chronological charts for:

- process PSS, native heap and Java heap;
- available device memory;
- Android thermal pressure.

Byte measurements are converted to MiB only in the presentation layer. Nullable measurements remain gaps, line segments do not bridge unavailable observations and `ThermalStatus.UNKNOWN` is not assigned an invented severity.

Charts are derived only from snapshots returned by `TelemetryRepository.recentResourceSnapshots()`. The console does not start polling or invoke the Android resource probe.

## Request detail and timeline

A selectable run or correlated log opens a detail screen with application/use-case identity, model digest prefix and load classification, queue/load/TTFT/prefill/decode/total latency, token counts, throughput, terminal status, typed error code and chronological structured-log events.

The timeline is reconstructed exclusively from persisted telemetry. Missing lifecycle transitions are not inferred. Explicit empty states cover a missing run, a run without logs and an unavailable telemetry source.

## Current standalone wiring

The standalone console uses:

- an in-memory telemetry repository inside its Android sandbox;
- a `FileSystemModelStore` rooted in its private files directory;
- `ModelStoreInventoryProvider` and `ModelStoreConsoleModelControl` over that store;
- a `HealthEngine` containing `ModelIntegrityHealthCheck` over that store;
- disconnected runtime and cache-control providers.

It can import, inspect, verify and remove only models in its own sandbox. It cannot inspect or mutate another application's runtime, telemetry, resources, model store or runtime-owned caches. It also cannot run generation sanity without a connected runtime.

An application embedding the console data layer in the same process can provide its actual runtime-state provider, model store, loaded-model digest supplier, health engine, cache probes, maintenance controls and telemetry repository.

A signature-protected diagnostics bridge remains required for legitimate cross-application access.

## Privacy and failures

The presenters do not receive or render prompts, generated output, model bytes, document URIs, private filesystem paths or arbitrary backend exception messages.

Failures are converted to fixed messages such as:

- `Telemetry source unavailable`;
- `Model inventory unavailable`;
- `Model management unavailable`;
- `Health execution unavailable`;
- `Cache health unavailable`;
- `Cache repair unavailable`.

Model-import error codes are mapped to fixed source, digest, size, destination-conflict or generic import failures. A model-management failure does not suppress telemetry, runtime state, inventory, health results or cache diagnostics.

Destroying the activity interrupts pending executor work through `shutdownNow()`. Generation-sanity timeout and cooperative cancellation remain owned by `GenerationSanityHealthCheck`.

## Testing

Pure JVM tests cover telemetry bounds and source isolation, runtime and inventory mapping, private-path removal, model-management capabilities and failures, import metadata forwarding, loaded-model removal blocking, action eligibility and disabling, health execution, request timelines, charts, cache probe isolation and targeted cache repair.

The repository validation gate compiles and packages the Android controls, Storage Access Framework picker, private staging flow and chart view; runs JVM tests, Spotless, ktlint, Detekt and Android Lint; verifies native packaging; and confirms that no model artifact is committed or bundled.

## Deferred slices

The following remain separate work:

- persistent console-local Room wiring when the console owns an embedded runtime;
- benchmark regression comparison and baseline history;
- manual inference playground;
- privacy-redacted diagnostic export;
- signature-protected cross-application diagnostics bridge.
