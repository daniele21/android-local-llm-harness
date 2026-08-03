# Local LLM Console observability foundation

## Scope

The console implementation turns `apps/local-llm-console` from a static shell into a read-only observability application.

It provides six primary views:

- overview;
- generation runs;
- structured logs;
- health and sanity results;
- memory and thermal snapshots;
- benchmark baselines.

Generation-run and request-correlated log cards can open a request detail view containing the complete persisted run metrics and a chronological event timeline.

The console uses the existing contracts from `observability/contracts`. It does not introduce alternate telemetry schemas or runtime policy.

## Architecture

```text
MainActivity
    ↓
ConsolePresenter
    ↓
ConsoleSnapshot / ConsoleRequestDetail
    ↑
ConsoleDataSource
    ↑
TelemetryConsoleDataSource
    ├── TelemetryRepository
    └── ConsoleRuntimeStateProvider
```

`ConsoleDataSource` is the application-facing read boundary. The Android activity receives already collected data and delegates all formatting, ordering and grouping to the pure Kotlin `ConsolePresenter`.

`TelemetryConsoleDataSource.load()` reads bounded collections from `TelemetryRepository`:

- recent generation runs;
- recent structured logs;
- persisted health results;
- recent resource snapshots;
- active benchmark baselines.

`TelemetryConsoleDataSource.loadRequest()` resolves one request through `findRun(requestId)` and retrieves only its correlated structured logs. Events are sorted by timestamp before they reach the presenter.

Runtime state is deliberately separated behind `ConsoleRuntimeStateProvider`. This prevents the console UI from depending directly on `RuntimeOrchestrator`, `llama.cpp`, Room or a future Binder transport.

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

The standalone console currently uses an in-memory repository inside its own Android sandbox and the disconnected runtime-state provider.

This is intentional. The console must not open another application's private Room database. Until a signature-protected diagnostics bridge is available, the UI shows unavailable runtime values explicitly rather than inventing a backend, loaded model, session count or queue depth.

The current slices therefore validate:

- console navigation and rendering;
- telemetry query boundaries;
- request selection and back navigation;
- request-correlated timeline reconstruction;
- privacy-safe empty and failure states;
- deterministic formatting and ordering;
- compatibility with future in-process or cross-application data sources.

They do not claim that the standalone console can already inspect another application.

## Privacy

The presenter uses the existing privacy-safe telemetry records. It does not receive or render:

- prompts;
- generated output;
- model bytes;
- document URIs;
- private filesystem paths;
- arbitrary backend exception messages.

Repository failures are converted to the fixed message `Telemetry source unavailable`. Raw exception messages are not shown.

Model digests are shortened in visual lists while the full request identifier remains available in the request detail.

## Failure behavior

If runtime-state collection fails, the console falls back to the disconnected state.

If a summary telemetry query fails, the snapshot contains empty collections and a fixed source error. If request-detail loading fails, the detail contains no run or events and the same fixed error. The activity remains usable and does not crash because an observability source is unavailable.

Refreshing while a request detail is open reloads both the summary snapshot and that request's correlated telemetry. Changing tab or using Back closes the detail without mutating runtime state.

## Testing

Pure JVM tests cover:

- bounded repository queries;
- runtime-state mapping;
- request lookup and request-scoped log filtering;
- chronological timeline ordering;
- privacy-safe repository failure handling;
- disconnected runtime empty states;
- run metric rendering;
- request-card selection metadata;
- timeline sequence and offsets;
- exclusion of prompt and output content;
- deterministic health ordering;
- deterministic structured-field ordering.

## Deferred slices

The following remain separate implementation work:

- persistent console-local Room wiring when the console owns an embedded runtime;
- installed-model and active-runtime views;
- health and sanity execution controls;
- memory and thermal charts;
- cache-health inspection and repair actions;
- benchmark regression comparison and history;
- installed-model management;
- manual inference playground;
- privacy-redacted diagnostic export;
- signature-protected cross-application diagnostics bridge.
