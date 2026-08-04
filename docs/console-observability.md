# Local LLM Console observability foundation

## Scope

The console implementation turns `apps/local-llm-console` from a static shell into an observability, runtime-inspection and explicit diagnostics-control application.

It provides nine primary views:

- overview;
- installed models;
- active runtime;
- generation runs;
- structured logs;
- health and sanity results and controls;
- cache health and repair controls;
- memory and thermal trends;
- benchmark baselines.

Generation-run and request-correlated log cards can open a request detail view containing complete persisted run metrics and a chronological event timeline.

The console reuses the existing contracts from `observability/contracts`, `observability/health-engine`, `core/contracts`, `core/runtime-core` and `models/model-store`. It does not introduce alternate telemetry schemas, model registries, health-check semantics, cache registries or runtime policy.

## Architecture

```text
MainActivity
    ├── ConsoleChartView
    ├── ConsoleHealthControl → HealthEngine
    ├── ConsoleCacheControl
    │       ├── CacheHealthProbe
    │       └── CacheMaintenanceControl
    └── presenters
            ├── ConsolePresenter
            ├── ConsoleHealthPresenter
            ├── ConsoleCachePresenter
            ├── ConsoleInventoryPresenter
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
            ├── ConsoleHealthControl.snapshot()
            └── ConsoleCacheControl.snapshot()
```

`ConsoleDataSource` is the application-facing read boundary. Runtime state, model inventory, health controls, cache diagnostics and persisted telemetry are loaded independently. Failure in one source does not prevent the remaining sources from rendering.

`ConsoleHealthControl` is the explicit health-execution boundary. Reading registered-check state does not execute a check, and executing a check does not bypass `HealthEngine` persistence, aggregation or exception isolation.

`ConsoleCacheControl` is the explicit cache-diagnostics boundary. It separates observational `CacheHealthProbe` instances from mutating `CacheMaintenanceControl` capabilities. Reading cache state never repairs or clears a cache.

Runtime state remains behind `ConsoleRuntimeStateProvider`. `LocalLlmRuntimeStateProvider` adapts the public `LocalLlmClient.runtimeSnapshot()` contract and does not depend on `RuntimeOrchestrator`, Room, Binder or backend implementation types.

Model inventory remains behind `ConsoleModelInventoryProvider`. `ModelStoreInventoryProvider` maps `ModelStoreSnapshot` into UI-safe values and deliberately drops `StoredModel.file`, so private filesystem paths never reach the presenter.

`ConsoleResourceChartPresenter` transforms persisted `ResourceSnapshot` values into Android-independent chart models. `ConsoleChartView` renders those models with Android Canvas primitives and does not query resource APIs, schedule timers or own telemetry collection.

## Installed-model view

The Models view shows source availability, aggregate model count and size, the active runtime model, full model digests, model size and integrity state.

It distinguishes:

- source not connected;
- connected inventory with zero installed models;
- inventory-source failure.

`ModelStore.snapshot()` is observational and does not hash every artifact. An entry with `verified = false` is shown as `Not checked`, not as corrupt. Explicit integrity execution remains owned by `HealthEngine`.

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

The Health view combines persisted results with explicit execution controls. A connected `ConsoleHealthControl` can expose:

- `Run all checks`;
- one targeted action for each registered check ID;
- source and execution state;
- persisted results ordered by severity.

Opening or refreshing the view never starts a check. Health work runs through a single-thread executor outside the Android main thread, and actions are disabled while execution is active. Completion reloads the same `TelemetryRepository`, so `HealthEngine` remains the only result owner.

The standalone console registers `ModelIntegrityHealthCheck` against its own sandboxed `FileSystemModelStore`. Generation-sanity actions are capability driven: registered `generation-sanity:<applicationId>:<useCaseId>` IDs appear automatically when an embedded runtime provides the corresponding checks. The standalone console does not create them because it has no connected `LocalLlmClient` or application/use-case binding.

The adapter preserves `HealthEngine` behavior for complete and targeted execution, worst-status aggregation, `NOT_RUN` results for unknown IDs, duration measurement, persistence and individual-check exception isolation.

## Cache health and repair controls

The Caches view reports each registered cache independently:

- cache identifier;
- total, healthy, stale and orphaned entry counts;
- health status;
- whether repair capability is available;
- source and execution state;
- last repair outcome.

The view distinguishes:

- diagnostics source not connected;
- connected source with no registered probes;
- healthy cache;
- unhealthy cache;
- individual probe failure;
- repair failure.

A repair action is rendered only when all of the following are true:

- the cache has a registered health probe;
- the latest snapshot is available;
- the cache is unhealthy;
- a matching `CacheMaintenanceControl` is registered.

Cache repair is explicit and runs through the same single-thread diagnostics executor used by health actions. No mutation occurs during discovery, refresh or overview rendering. While repair is running, the action is disabled.

### Model-integrity cache repair

`ModelIntegrityCacheMaintenanceControl` operates on the runtime-owned `ModelIntegrityCache` and its injected `ModelStore`.

Repair behavior is targeted:

- stale entries are revalidated through `ModelStore.verify()`;
- successfully revalidated entries receive a current file stamp;
- stale entries whose model verification fails are removed from the cache;
- orphaned entries whose model no longer exists are removed;
- revalidation exceptions are counted as failures and leave the stale entry visible for a later retry.

The result reports before and after snapshots plus revalidated, removed and failed counts.

The console does not expose `ModelIntegrityCache.clear()` as repair. Blindly clearing the cache would discard the historical file stamp used to detect that an artifact changed and could cause an unchanged `StoredModel.verified` flag to seed the cache again without the intended re-hash.

Concurrent changes are handled with conditional `ConcurrentHashMap.remove(key, value)` and `replace(key, oldValue, newValue)` operations. A repair does not overwrite an entry that changed after the snapshot was captured.

The standalone console intentionally uses `DisconnectedCacheControl`: its process does not own the embedded runtime's integrity cache. An embedding application can provide `ContractConsoleCacheControl` with `ModelIntegrityCacheHealthProbe` and `ModelIntegrityCacheMaintenanceControl` over the same runtime-owned cache instance.

## Resource and thermal charts

The Resources view retains persisted snapshot cards and adds three chronological charts:

- process PSS, native heap and Java heap;
- available device memory;
- Android thermal pressure from `NONE` through `SHUTDOWN`.

Byte measurements are converted to MiB only in the presentation layer. Samples are ordered by timestamp and the horizontal axis shows elapsed time from the first sample.

Nullable measurements remain gaps. Missing memory values are not converted to zero, line segments do not bridge unavailable observations and `ThermalStatus.UNKNOWN` is not assigned an invented severity. The thermal chart also reports persisted `lowMemory = true` samples without interpreting `null` as false.

Charts are derived only from snapshots returned by `TelemetryRepository.recentResourceSnapshots()`. The console does not start polling or invoke the Android resource probe.

## Request detail and timeline

A selectable run or correlated log opens a detail screen with:

- application and use-case identity;
- model digest prefix and load classification;
- queue, model-load, TTFT, prefill, decode and total latency;
- token counts and decode throughput;
- terminal status and typed error code;
- chronological structured-log events;
- sequence number, absolute timestamp and run-relative offset;
- component and deterministically ordered structured fields.

The timeline is reconstructed exclusively from persisted telemetry. Missing lifecycle transitions are not inferred. Explicit empty states cover a missing run, a run without logs and an unavailable telemetry source.

## Current wiring

The standalone console currently uses:

- an in-memory telemetry repository inside its Android sandbox;
- a `FileSystemModelStore` rooted in its private files directory;
- a `HealthEngine` containing `ModelIntegrityHealthCheck` over that store;
- disconnected runtime and cache-control providers.

It can inspect and verify only its own model-store namespace. It cannot inspect another application's runtime, telemetry, resources, model store or runtime-owned caches. It also cannot run generation sanity without a connected runtime.

An application embedding the console data layer in the same process can provide:

- `LocalLlmRuntimeStateProvider` over its `LocalLlmClient`;
- `ModelStoreInventoryProvider` over its real `ModelStore`;
- `HealthEngineConsoleHealthControl` over registered health and sanity checks;
- `ContractConsoleCacheControl` over runtime-owned cache probes and maintenance controls;
- its persistent or in-memory `TelemetryRepository`.

A signature-protected diagnostics bridge remains required for legitimate cross-application access.

## Privacy and failures

The presenters do not receive or render prompts, generated output, model bytes, document URIs, private filesystem paths or arbitrary backend exception messages.

Failures are converted to fixed messages:

- `Telemetry source unavailable`;
- `Model inventory unavailable`;
- `Health execution unavailable`;
- `Cache health unavailable`;
- `Cache repair unavailable`.

A cache-source failure does not suppress telemetry, model inventory, runtime state or persisted health results. One failing cache probe does not hide other cache snapshots. A repair exception clears the running state and produces a fixed error without exposing the original exception text.

Destroying the activity interrupts pending executor work through `shutdownNow()`. Generation-sanity timeout and cooperative cancellation remain owned by `GenerationSanityHealthCheck`.

## Testing

Pure JVM tests cover:

- bounded telemetry queries and independent source failures;
- runtime and model-store adapter mapping;
- private path removal;
- connected, disconnected and empty inventory states;
- health discovery, complete and targeted execution and persistence;
- request lookup, filtering, ordering and timeline offsets;
- chart ordering, MiB conversion, nullable gaps and thermal mapping;
- cache-probe ordering and per-probe failure isolation;
- maintenance capability matching;
- disconnected, empty, healthy, unhealthy and running cache presentation;
- repair-action eligibility and disabling;
- repair before/after presentation;
- stale entry revalidation;
- orphaned and invalid entry removal;
- failed revalidation retention;
- privacy-safe cache-source and repair errors.

The repository validation gate compiles and packages the Android controls and chart view, runs JVM tests, Spotless, Detekt and Android Lint, verifies native packaging and confirms that no model artifact is introduced.

## Deferred slices

The following remain separate work:

- persistent console-local Room wiring when the console owns an embedded runtime;
- benchmark regression comparison and baseline history;
- installed-model mutation and management;
- manual inference playground;
- privacy-redacted diagnostic export;
- signature-protected cross-application diagnostics bridge.
