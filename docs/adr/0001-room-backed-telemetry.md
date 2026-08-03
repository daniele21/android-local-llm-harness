# ADR 0001 — Room-backed persistent telemetry

- Status: Accepted
- Date: 2026-08-03

## Context

The embedded runtime needs telemetry that survives process restarts and supports bounded run timelines, structured logs and health results. The runtime must remain independent from Android persistence details, normal telemetry must exclude prompts and generated content, and an observability failure must never break inference.

The existing `TelemetryRepository` contract and in-memory implementation already establish the domain boundary, but the in-memory repository cannot support durable diagnostics or later control-plane queries.

Android Gradle Plugin 9 uses built-in Kotlin support in this repository. The persistence implementation should therefore avoid introducing a second Kotlin compilation path merely for annotation processing.

## Decision

Use Android Room as the first persistent implementation behind `TelemetryRepository` in a dedicated `observability/room-store` module.

The implementation will:

- keep Room entities, DAO and database types out of observability contracts and runtime orchestration;
- use Java Room entities and DAO types with the standard Java annotation processor;
- execute all database work on one dedicated executor while preserving the current synchronous repository API;
- replace the run record for a stable request ID as lifecycle state advances;
- append structured log events correlated by request ID;
- retain only the latest bounded number of runs and logs;
- keep the latest result for each health-check ID;
- store identifiers, status, error codes, timings, token counts, throughput and explicitly bounded fields only;
- exclude prompts, generated output, arbitrary exception messages and model bytes;
- treat repository failures as best-effort diagnostic failures rather than inference failures.

The separate developer console will not directly open another application's private Room database. Embedded cross-application diagnostics remain dependent on the future signature-protected diagnostics bridge; the shared-runtime phase may centralize ownership later.

## Consequences

### Positive

- Telemetry survives process restarts without coupling runtime orchestration to Android APIs.
- The in-memory and Room implementations share query and retention semantics.
- Room owns schema verification and generated database code.
- A single serialized database executor provides deterministic ordering and avoids main-thread access.
- Future console or diagnostics transports can query stable repository contracts rather than database internals.

### Costs and constraints

- The synchronous repository contract blocks the calling thread until the dedicated database executor completes. Callers must avoid high-volume token-level writes; the runtime records lifecycle transitions and aggregated metrics only.
- Schema changes require explicit Room migrations after version 1 is released.
- The Room database is scoped to its Android application sandbox during the embedded phase.
- Health, benchmark and diagnostic-export schemas will extend this domain and must preserve privacy defaults.

## Alternatives considered

### SQLite APIs directly

Rejected for the first implementation because Room provides compile-time query/schema validation and clearer entity ownership with less custom mapping and migration infrastructure.

### Persist JSON files

Rejected because updates, ordering, bounded retention and indexed request correlation would require custom transactional behavior and become fragile under process interruption.

### Persist every generation event or token delta

Rejected because it increases write volume and risks persisting generated content. The selected design stores lifecycle transitions, aggregate metrics and explicitly structured metadata only.

### Make Room types the public telemetry API

Rejected because it would couple runtime, future Binder contracts and non-Android tests to one persistence implementation.
