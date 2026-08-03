# Local LLM Console observability foundation

## Scope

The first console implementation slice turns `apps/local-llm-console` from a static shell into a read-only observability application.

It provides six views:

- overview;
- generation runs;
- structured logs;
- health and sanity results;
- memory and thermal snapshots;
- benchmark baselines.

The console uses the existing contracts from `observability/contracts`. It does not introduce alternate telemetry schemas or runtime policy.

## Architecture

```text
MainActivity
    ↓
ConsolePresenter
    ↓
ConsoleSnapshot
    ↑
ConsoleDataSource
    ↑
TelemetryConsoleDataSource
    ├── TelemetryRepository
    └── ConsoleRuntimeStateProvider
```

`ConsoleDataSource` is the application-facing read boundary. The Android activity receives already collected data and delegates all formatting and grouping to the pure Kotlin `ConsolePresenter`.

`TelemetryConsoleDataSource` reads bounded collections from `TelemetryRepository`:

- recent generation runs;
- recent structured logs;
- persisted health results;
- recent resource snapshots;
- active benchmark baselines.

Runtime state is deliberately separated behind `ConsoleRuntimeStateProvider`. This prevents the console UI from depending directly on `RuntimeOrchestrator`, `llama.cpp`, Room or a future Binder transport.

## Current wiring

The standalone console currently uses an in-memory repository inside its own Android sandbox and the disconnected runtime-state provider.

This is intentional. The console must not open another application's private Room database. Until a signature-protected diagnostics bridge is available, the UI shows unavailable runtime values explicitly rather than inventing a backend, loaded model, session count or queue depth.

The current slice therefore validates:

- console navigation and rendering;
- telemetry query boundaries;
- privacy-safe empty and failure states;
- deterministic formatting and ordering;
- compatibility with future in-process or cross-application data sources.

It does not claim that the standalone console can already inspect another application.

## Privacy

The presenter uses the existing privacy-safe telemetry records. It does not receive or render:

- prompts;
- generated output;
- model bytes;
- document URIs;
- private filesystem paths;
- arbitrary backend exception messages.

Repository failures are converted to the fixed message `Telemetry source unavailable`. Raw exception messages are not shown.

Model digests are shortened in the visual list while the full request identifier remains available in the request card.

## Failure behavior

If runtime-state collection fails, the console falls back to the disconnected state.

If a telemetry query fails, the snapshot contains empty collections and a fixed source error. The activity remains usable and does not crash because an observability source is unavailable.

## Testing

Pure JVM tests cover:

- bounded repository queries;
- runtime-state mapping;
- privacy-safe repository failure handling;
- disconnected runtime empty states;
- run metric rendering;
- exclusion of prompt and output content;
- deterministic health ordering;
- deterministic structured-field ordering.

## Deferred slices

The following remain separate implementation work:

- persistent console-local Room wiring when the console owns an embedded runtime;
- request selection and correlated event timeline;
- health and sanity execution controls;
- memory and thermal charts;
- cache-health inspection and repair actions;
- benchmark regression comparison and history;
- installed-model management;
- manual inference playground;
- privacy-redacted diagnostic export;
- signature-protected cross-application diagnostics bridge.
