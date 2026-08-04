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
- benchmark regressions and retained baseline history.

Generation-run and request-correlated log cards can open a request detail view containing complete persisted run metrics and a chronological event timeline.

The console reuses the existing contracts from `observability/contracts`, `observability/health-engine`, `observability/benchmark-engine`, `core/contracts`, `core/runtime-core` and `models/model-store`. It does not introduce alternate telemetry schemas, benchmark policies, model registries, health-check semantics, cache registries or runtime policy.

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
            ├── ConsoleBenchmarkPresenter
            ├── ConsoleInventoryPresenter
            └── ConsoleResourceChartPresenter
                    ↓
        ConsoleSnapshot / ConsoleRequestDetail
                    ↑
            ConsoleDataSource
                    ↑
        TelemetryConsoleDataSource
            ├── TelemetryRepository
            ├── BenchmarkComparisonEvaluator
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

`ConsoleResourceChartPresenter` transforms persisted `ResourceSnapshot` values into Android-independent chart models. `ConsoleBenchmarkPresenter` transforms structured comparisons and retained baseline captures into cards and the same generic chart models. `ConsoleChartView` renders those models with Android Canvas primitives and does not query resource APIs, schedule timers or own telemetry collection.

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

The view distinguishes diagnostics not connected, connected with no probes, healthy, unhealthy, individual probe failure and repair failure.

A repair action is rendered only when the cache has an available unhealthy snapshot and a matching `CacheMaintenanceControl`. Repair is explicit, runs outside the main thread and is disabled while active. No mutation occurs during discovery, refresh or overview rendering.

### Model-integrity cache repair

`ModelIntegrityCacheMaintenanceControl` operates on the runtime-owned `ModelIntegrityCache` and its injected `ModelStore`.

Repair behavior is targeted:

- stale entries are revalidated through `ModelStore.verify()`;
- successfully revalidated entries receive a current file stamp;
- stale entries whose verification fails are removed;
- orphaned entries are removed;
- revalidation exceptions are counted as failures and leave the stale entry visible for retry.

The result reports before and after snapshots plus revalidated, removed and failed counts. The console does not expose `ModelIntegrityCache.clear()` as repair because blind clearing would discard the historical stamp used to detect changed artifacts.

Conditional concurrent-map remove and replace operations prevent a repair from overwriting an entry changed after snapshot capture.

The standalone console intentionally uses `DisconnectedCacheControl`: its process does not own the embedded runtime cache. An embedding application can provide probes and maintenance controls over the same runtime-owned cache instance.

## Resource and thermal charts

The Resources view retains persisted snapshot cards and adds three chronological charts:

- process PSS, native heap and Java heap;
- available device memory;
- Android thermal pressure from `NONE` through `SHUTDOWN`.

Byte measurements are converted to MiB only in the presentation layer. Samples are ordered by timestamp and the horizontal axis shows elapsed time from the first sample.

Nullable measurements remain gaps. Missing memory values are not converted to zero, line segments do not bridge unavailable observations and `ThermalStatus.UNKNOWN` is not assigned an invented severity. The thermal chart also reports persisted `lowMemory = true` samples without interpreting `null` as false.

Charts are derived only from snapshots returned by `TelemetryRepository.recentResourceSnapshots()`. The console does not start polling or invoke the Android resource probe.

## Benchmark regressions and baseline history

The Benchmarks view combines one active baseline per benchmark key with bounded retained capture history.

The view includes:

- active-key, retained-capture, PASS, WARN and FAIL summary counts;
- one comparison card per active benchmark key;
- application, use case, model digest prefix and cold/warm load class;
- active-baseline timestamp and sample count;
- available and required post-baseline sample counts;
- baseline and current values for median TTFT, p95 total latency and median decode throughput;
- current-to-baseline ratios and configured policy thresholds;
- explicit `Within policy`, `Regression`, `Preview` or `Unavailable` metric states;
- one card per retained baseline capture, including whether that capture remains active;
- chronological history charts for median TTFT, p95 total latency and median decode throughput.

`TelemetryConsoleDataSource` uses `BenchmarkComparisonEvaluator`, the same evaluator used by `BenchmarkRegressionHealthCheck`. The console therefore does not duplicate matching rules, sample readiness or threshold logic.

The comparison lookback is independent from the visible Runs limit. Reducing the number of generation cards shown in the console does not silently reduce the benchmark comparison window.

Only completed post-baseline runs matching application, use case, immutable model digest and explicit cold/warm load class are compared. When the minimum comparison window is not complete, available aggregates and ratios are shown only as a non-actionable preview. They do not become PASS or FAIL evidence.

Retained history is bounded by `TelemetryRetentionPolicy.maxBenchmarkBaselines`. The active baseline remains stored separately, so history retention never removes the current comparison anchor. Room schema version 4 adds a history table through a non-destructive migration and copies existing active baselines into that history.

Nullable historical metrics remain chart gaps. A missing p95 or throughput value is not converted to zero or connected across unavailable observations.

The standalone console can render benchmark data only when its telemetry repository contains baseline captures and matching post-baseline runs. It does not create baselines or execute workloads implicitly.

## Request detail and timeline

A selectable run or correlated log opens a detail screen with application/use-case identity, model digest prefix, load classification, latency metrics, token counts, throughput, terminal state and a chronological structured-log timeline.

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
- cache probes and maintenance controls over runtime-owned caches;
- its persistent or in-memory `TelemetryRepository` with active baselines and retained history.

A signature-protected diagnostics bridge remains required for legitimate cross-application access.

## Privacy and failures

The presenters do not receive or render prompts, generated output, model bytes, document URIs, private filesystem paths or arbitrary backend exception messages.

Failures are converted to fixed telemetry, inventory, health and cache errors. A cache-source failure does not suppress telemetry, model inventory, runtime state or persisted health results. Benchmark comparison uses only structured latency, throughput, identifiers and load classification already permitted by telemetry contracts.

Destroying the activity interrupts pending executor work through `shutdownNow()`. Generation-sanity timeout and cooperative cancellation remain owned by `GenerationSanityHealthCheck`.

## Testing

Pure JVM tests cover:

- bounded telemetry queries and independent source failures;
- runtime and model-store adapter mapping and private-path removal;
- health discovery, execution and persistence;
- request lookup, filtering, ordering and timeline offsets;
- resource-chart ordering, MiB conversion, nullable gaps and thermal mapping;
- cache probe isolation, maintenance matching and repair outcomes;
- benchmark active-baseline replacement and bounded history retention;
- Room active/history transactional persistence;
- structured benchmark PASS, WARN and FAIL comparisons;
- multi-metric regression ratios and thresholds;
- partial non-actionable previews;
- comparison lookback independent from visible run limits;
- chronological benchmark history cards and charts;
- nullable benchmark metrics preserved as chart gaps;
- privacy-safe benchmark health details.

The repository validation gate compiles and packages the Android controls and chart views, runs JVM tests, Spotless, Detekt and Android Lint, verifies native packaging and confirms that no model artifact is introduced.

## Deferred slices

The following remain separate work:

- persistent console-local Room wiring when the console owns an embedded runtime;
- installed-model mutation and management;
- manual inference playground;
- privacy-redacted diagnostic export;
- signature-protected cross-application diagnostics bridge.
