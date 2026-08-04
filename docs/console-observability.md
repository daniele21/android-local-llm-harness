# Local LLM Console observability foundation

## Scope

The console implementation turns `apps/local-llm-console` from a static shell into a read-only observability and runtime-inspection application.

It provides eight primary views:

- overview;
- installed models;
- active runtime;
- generation runs;
- structured logs;
- health and sanity results;
- memory and thermal snapshots;
- benchmark baselines.

Generation-run and request-correlated log cards can open a request detail view containing the complete persisted run metrics and a chronological event timeline.

The console reuses the existing contracts from `observability/contracts`, `core/contracts` and `models/model-store`. It does not introduce alternate telemetry schemas, model registries or runtime policy.

## Architecture

```text
MainActivity
    ↓
ConsolePresenter
    ├── ConsoleInventoryPresenter
    └── request and observability presentation
    ↓
ConsoleSnapshot / ConsoleRequestDetail
    ↑
ConsoleDataSource
    ↑
TelemetryConsoleDataSource
    ├── TelemetryRepository
    ├── ConsoleRuntimeStateProvider
    └── ConsoleModelInventoryProvider
            ↑
            ModelStoreInventoryProvider → ModelStore.snapshot()
```

`ConsoleDataSource` is the application-facing read boundary. The Android activity receives already collected data and delegates formatting, ordering and grouping to pure Kotlin presenters.

`TelemetryConsoleDataSource.load()` reads bounded collections from `TelemetryRepository`:

- recent generation runs;
- recent structured logs;
- persisted health results;
- recent resource snapshots;
- active benchmark baselines.

It also loads runtime and model-inventory snapshots through independent providers. A failure in one source does not prevent the remaining sources from rendering.

`TelemetryConsoleDataSource.loadRequest()` resolves one request through `findRun(requestId)` and retrieves only its correlated structured logs. Events are sorted by timestamp before they reach the presenter.

Runtime state remains separated behind `ConsoleRuntimeStateProvider`. `LocalLlmRuntimeStateProvider` adapts the public `LocalLlmClient.runtimeSnapshot()` contract and therefore does not depend on `RuntimeOrchestrator`, `llama.cpp`, Room or Binder implementation types.

Model inventory remains separated behind `ConsoleModelInventoryProvider`. `ModelStoreInventoryProvider` maps the existing content-addressed `ModelStoreSnapshot` into UI-safe values and deliberately drops `StoredModel.file`, so private filesystem paths never reach the presenter.

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

The UI explicitly states that session descriptors, context parameters and active-request identity are not exposed by the current `RuntimeSnapshot`. It does not infer or fabricate these values. Runtime controls remain outside this read-only slice.

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
- the disconnected runtime-state provider.

This means the standalone console can accurately inspect its own local model-store namespace, including an empty store, but it cannot inspect a different application's model store or runtime.

This is intentional. The console must not open another application's private Room database or private model directory. Until a signature-protected diagnostics bridge is available, cross-application runtime values remain explicitly unavailable.

An application embedding the console data layer in the same process can provide:

- `LocalLlmRuntimeStateProvider` over its `LocalLlmClient`;
- `ModelStoreInventoryProvider` over its real `ModelStore`;
- its own persistent or in-memory `TelemetryRepository`.

The UI and presenters do not need to change when these providers replace the standalone wiring.

## Privacy

The presenters do not receive or render:

- prompts;
- generated output;
- model bytes;
- document URIs;
- private filesystem paths;
- arbitrary backend exception messages.

Telemetry failures are converted to `Telemetry source unavailable`. Model-store failures are converted to `Model inventory unavailable`. Raw exception messages are not shown.

Model inventory mapping retains digest, size and integrity state but drops the backing `File` reference before the data reaches the presentation layer.

## Failure behavior

If runtime-state collection fails, the console falls back to the disconnected state.

If model-inventory collection fails, the model source becomes unavailable with a fixed privacy-safe error while telemetry remains usable.

If a summary telemetry query fails, the snapshot contains empty telemetry collections and a fixed source error while runtime and model inventory remain available. If request-detail loading fails, the detail contains no run or events and the same fixed telemetry error.

Refreshing while a request detail is open reloads both the summary snapshot and that request's correlated telemetry. Changing tab or using Back closes the detail without mutating runtime or model state.

## Testing

Pure JVM tests cover:

- bounded repository queries;
- runtime-state mapping;
- model-inventory provider mapping;
- removal of private model paths at the adapter boundary;
- connected, disconnected and empty inventory states;
- active-model correlation;
- explicit runtime-contract gaps;
- request lookup and request-scoped log filtering;
- chronological timeline ordering;
- privacy-safe source failure handling;
- run metric rendering;
- request-card selection metadata;
- timeline sequence and offsets;
- exclusion of prompt and output content;
- deterministic health ordering;
- deterministic structured-field ordering.

## Deferred slices

The following remain separate implementation work:

- persistent console-local Room wiring when the console owns an embedded runtime;
- health and sanity execution controls;
- memory and thermal charts;
- cache-health inspection and repair actions;
- benchmark regression comparison and history;
- installed-model mutation and management;
- manual inference playground;
- privacy-redacted diagnostic export;
- signature-protected cross-application diagnostics bridge.
