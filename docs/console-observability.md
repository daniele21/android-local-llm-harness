# Local LLM Console observability foundation

## Scope

The console implementation turns `apps/local-llm-console` from a static shell into an observability, runtime-inspection and explicit health-control application.

It provides eight primary views:

- overview;
- installed models;
- active runtime;
- generation runs;
- structured logs;
- health and sanity results and controls;
- memory and thermal trends;
- benchmark baselines.

Generation-run and request-correlated log cards can open a request detail view containing the complete persisted run metrics and a chronological event timeline.

The console reuses the existing contracts from `observability/contracts`, `observability/health-engine`, `core/contracts` and `models/model-store`. It does not introduce alternate telemetry schemas, model registries, health-check execution semantics or runtime policy.

## Architecture

```text
MainActivity
    ├── ConsoleChartView
    ├── ConsoleHealthControl → HealthEngine
    └── presenters
            ├── ConsolePresenter
            ├── ConsoleHealthPresenter
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
            └── ConsoleHealthControl.snapshot()
                    ↑
                    ModelStoreInventoryProvider → ModelStore.snapshot()
```

`ConsoleDataSource` is the application-facing read boundary. The Android activity receives already collected data and delegates formatting, ordering and grouping to pure Kotlin presenters.

`ConsoleHealthControl` is a separate execution boundary. Reading registered-check state does not execute a check, and executing a check does not bypass `HealthEngine` persistence, status aggregation or error-isolation behavior.

`TelemetryConsoleDataSource.load()` reads bounded collections from `TelemetryRepository`:

- recent generation runs;
- recent structured logs;
- persisted health results;
- recent resource snapshots;
- active benchmark baselines.

It also loads runtime, model-inventory and health-control snapshots through independent providers. A failure in one source does not prevent the remaining sources from rendering.

`TelemetryConsoleDataSource.loadRequest()` resolves one request through `findRun(requestId)` and retrieves only its correlated structured logs. Events are sorted by timestamp before they reach the presenter.

Runtime state remains separated behind `ConsoleRuntimeStateProvider`. `LocalLlmRuntimeStateProvider` adapts the public `LocalLlmClient.runtimeSnapshot()` contract and therefore does not depend on `RuntimeOrchestrator`, `llama.cpp`, Room or Binder implementation types.

Model inventory remains separated behind `ConsoleModelInventoryProvider`. `ModelStoreInventoryProvider` maps the existing content-addressed `ModelStoreSnapshot` into UI-safe values and deliberately drops `StoredModel.file`, so private filesystem paths never reach the presenter.

`HealthEngineConsoleHealthControl` adapts the existing `HealthEngine.availableChecks()`, `runAll()` and targeted `run(checkIds)` contracts. It does not invoke individual `HealthCheck` implementations directly.

`ConsoleResourceChartPresenter` transforms persisted `ResourceSnapshot` values into Android-independent chart models. `ConsoleChartView` renders those models with Android Canvas primitives and does not query resource APIs, schedule timers or own telemetry collection.

## Installed-model view

The installed-model view shows:

- whether the inventory source is available;
- model count;
- aggregate stored bytes;
- source identity;
- active runtime model;
- full SHA-256 digest per model;
- model size;
- integrity state;
- whether the model is installed or currently loaded.

The view distinguishes three different states:

- inventory source not connected;
- connected inventory with zero installed models;
- inventory source failure.

`ModelStore.snapshot()` is observational and does not hash every artifact. An entry returned with `verified = false` is therefore shown as `Not checked`, not as corrupt or invalid. Explicit integrity execution remains owned by the health-engine slice.

## Active-runtime view

The active-runtime view shows only fields available from the public runtime snapshot:

- connection state;
- runtime state;
- backend identity supplied by the adapter;
- loaded model digest;
- active session count;
- queued-request count;
- source identity.

The UI explicitly states that session descriptors, context parameters and active-request identity are not exposed by the current `RuntimeSnapshot`. It does not infer or fabricate these values. Runtime mutation remains outside this slice.

## Health and sanity execution controls

The Health view combines persisted results with explicit execution controls. When a connected `ConsoleHealthControl` reports registered checks, the view provides:

- `Run all checks`;
- one targeted action for each registered check ID;
- source identity;
- ready or running state;
- registered-check count and IDs;
- persisted results ordered by severity.

Execution is user initiated. Opening the view, refreshing the console or reading `ConsoleHealthControlState` never starts a health check.

The Android activity runs health work through a dedicated single-thread executor rather than the main thread. While one suite is running, all health actions are disabled. Completion reloads the same `TelemetryRepository`, so results persisted by `HealthEngine` become visible without a parallel result store.

The standalone console registers `ModelIntegrityHealthCheck` against its own sandboxed `FileSystemModelStore`. It can therefore run a real integrity check only for artifacts owned by the console application.

Generation sanity controls are capability driven rather than hard-coded into the UI. An embedded runtime can provide a `HealthEngineConsoleHealthControl` whose engine contains one or more `GenerationSanityHealthCheck` instances. Their stable `generation-sanity:<applicationId>:<useCaseId>` IDs then appear automatically as targeted actions. The standalone console does not create generation sanity checks because it has no connected `LocalLlmClient`, application/use-case binding or runtime-owned model lifecycle.

The control adapter retains `HealthEngine` behavior for:

- complete and targeted execution;
- worst-status aggregation;
- explicit `NOT_RUN` results for unknown IDs;
- duration measurement;
- persisted privacy-safe results;
- individual check exception isolation.

The console does not add cancellation or concurrent-suite execution semantics that are absent from the current `HealthEngine` contract.

## Resource and thermal charts

The Resources view retains the individual persisted snapshot cards and adds three chronological charts:

- process memory, with process PSS, native heap and Java heap series;
- available device memory;
- Android thermal pressure from `NONE` through `SHUTDOWN`.

Byte measurements are converted to MiB only in the presentation layer. Samples are ordered by `timestampEpochMs`, while the horizontal axis shows elapsed time from the first persisted sample.

Nullable measurements are preserved as gaps. A missing PSS, heap or available-memory value does not become zero and does not connect line segments across unavailable observations. `ThermalStatus.UNKNOWN` is also rendered as a gap rather than being assigned an invented severity.

The thermal chart additionally reports how many persisted samples carried `lowMemory = true`. It does not reinterpret `null` as false.

Charts are derived only from snapshots already returned by `TelemetryRepository.recentResourceSnapshots()`. The console does not install a timer, start background polling or invoke the Android resource probe. Capture cadence remains explicitly owned by the embedding application or runtime control plane.

If no resource snapshots exist, no placeholder graph is created and the existing empty-state card remains authoritative.

## Request detail and timeline

A selectable run or correlated log opens a detail screen with:

- application and use-case identity;
- model digest prefix and load classification;
- queue and model-load duration;
- TTFT, prefill, decode and total latency;
- input and output token counts;
- decode throughput;
- terminal status and typed error code;
- chronological structured-log events;
- event sequence number;
- absolute timestamp;
- offset from the run start, or from the first event when the run record is unavailable;
- component and deterministically ordered structured fields.

The timeline is reconstructed exclusively from persisted telemetry. It does not infer missing lifecycle transitions and does not fabricate events.

The request detail supports explicit empty states for:

- a request identifier without a matching run record;
- a run without correlated structured logs;
- an unavailable telemetry source.

## Current wiring

The standalone console currently uses:

- an in-memory telemetry repository inside its own Android sandbox;
- a `FileSystemModelStore` rooted in the console application's private files directory;
- a `HealthEngine` containing `ModelIntegrityHealthCheck` over that same store;
- the disconnected runtime-state provider.

This means the standalone console can accurately inspect and verify its own local model-store namespace, including an empty store, but it cannot inspect or verify a different application's model store. It also cannot run generation sanity without a connected runtime. The chart layer is complete, but the standalone in-memory repository contains no resource history until a real diagnostics source is connected.

This is intentional. The console must not open another application's private Room database or private model directory. Until a signature-protected diagnostics bridge is available, cross-application runtime values, health execution, model inventory and resource history remain explicitly unavailable.

An application embedding the console data layer in the same process can provide:

- `LocalLlmRuntimeStateProvider` over its `LocalLlmClient`;
- `ModelStoreInventoryProvider` over its real `ModelStore`;
- `HealthEngineConsoleHealthControl` over its registered integrity and generation-sanity checks;
- its own persistent or in-memory `TelemetryRepository`.

The UI, chart renderer and presenters do not need to change when these providers replace the standalone wiring.

## Privacy

The presenters do not receive or render:

- prompts;
- generated output;
- model bytes;
- document URIs;
- private filesystem paths;
- arbitrary backend exception messages.

Telemetry failures are converted to `Telemetry source unavailable`. Model-store failures are converted to `Model inventory unavailable`. Health-control failures are converted to `Health execution unavailable`. Raw exception messages are not shown.

Model inventory mapping retains digest, size and integrity state but drops the backing `File` reference before the data reaches the presentation layer.

Health execution renders only the privacy-safe `HealthCheckResult` values already produced by `HealthEngine`. The console does not add prompts, generated output or backend exception text to those records.

Resource charts contain only the numeric process and device measurements already present in `ResourceSnapshot`; they do not add identifiers, file paths or content payloads.

## Failure behavior

If runtime-state collection fails, the console falls back to the disconnected state.

If model-inventory collection fails, the model source becomes unavailable with a fixed privacy-safe error while telemetry remains usable.

If health-control discovery fails, execution controls become unavailable with a fixed privacy-safe error while persisted telemetry remains readable. If suite execution fails at the adapter boundary, the UI clears the running state and displays the same fixed error.

If a summary telemetry query fails, the snapshot contains empty telemetry collections and a fixed source error while runtime, model inventory and health-control discovery remain independently available. If request-detail loading fails, the detail contains no run or events and the same fixed telemetry error.

Refreshing while a request detail is open reloads both the summary snapshot and that request's correlated telemetry. Changing tab or using Back closes the detail without mutating runtime or model state.

Destroying the activity interrupts pending executor work through `shutdownNow()`. Individual generation-sanity timeout and cooperative cancellation remain owned by `GenerationSanityHealthCheck`.

## Testing

Pure JVM tests cover:

- bounded repository queries;
- runtime-state mapping;
- model-inventory provider mapping;
- removal of private model paths at the adapter boundary;
- connected, disconnected and empty inventory states;
- active-model correlation;
- explicit runtime-contract gaps;
- health-control state discovery and isolation;
- complete and targeted health execution through `HealthEngine`;
- persisted health results;
- worst-status propagation;
- disconnected health execution with a fixed error;
- connected and disconnected action presentation;
- action disabling during execution;
- deterministic persisted-result severity ordering;
- request lookup and request-scoped log filtering;
- chronological timeline ordering;
- privacy-safe source failure handling;
- run metric rendering;
- request-card selection metadata;
- timeline sequence and offsets;
- exclusion of prompt and output content;
- deterministic structured-field ordering;
- chronological resource-sample ordering;
- byte-to-MiB conversion;
- missing-value gaps without invented zeros;
- discrete thermal-state mapping and `UNKNOWN` gaps;
- low-memory signal counting;
- empty resource history without placeholder charts.

The repository validation gate also compiles and packages the Android health controls and custom chart view, runs Android Lint and Detekt, and verifies that no model artifact is introduced.

## Deferred slices

The following remain separate implementation work:

- persistent console-local Room wiring when the console owns an embedded runtime;
- cache-health inspection and repair actions;
- benchmark regression comparison and history;
- installed-model mutation and management;
- manual inference playground;
- privacy-redacted diagnostic export;
- signature-protected cross-application diagnostics bridge.
