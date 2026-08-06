# Harness connected telemetry composition

Status: active
Document type: feature-specification
Owner: apps/local-llm-phone-test
Canonical scope: phone.diagnostics.telemetry
Read when: changing connected-app telemetry assembly, retention or timeline presentation
Last reviewed: 2026-08-06

**Status:** Implemented first iteration
**Application:** `apps/local-llm-phone-test`
**Last updated:** 2026-08-04

## Decision

The first connected Harness iteration uses one process-scoped `InMemoryTelemetryRepository` owned by `HarnessRuntimeGraph`.

The same repository is injected into the single shared `RuntimeOrchestrator` used by Playground and physical-device validation. Diagnostics reads from this repository through `HarnessDiagnosticsSource`; it does not create a parallel runtime, model store, telemetry store, or registry.

## Rationale

The in-memory implementation is selected for the first connected vertical slice because it:

- uses the existing stable `TelemetryRepository` contract;
- avoids introducing a Room schema and migration lifecycle into the Compose/runtime refactor PR;
- makes real generation runs and structured logs immediately observable;
- preserves bounded retention;
- keeps telemetry ownership inside the application process;
- allows a later transition to `RoomTelemetryRepository` without changing Diagnostics presentation contracts.

## Retention

The connected graph retains at most:

- 200 generation runs;
- 1,000 structured logs;
- 200 resource snapshots.

Diagnostics uses smaller bounded query windows for presentation.

## Lifecycle

- Constructing `HarnessRuntimeGraph` creates the telemetry repository but does not load llama.cpp or a GGUF model.
- The runtime is created lazily when an operation requests a `PhoneHarness`.
- Replacing or removing the active model closes the runtime but does not clear process telemetry.
- Activity recreation does not own the process-scoped repository.
- Android process death clears the in-memory telemetry history.

## Privacy

Normal telemetry includes identifiers, lifecycle status, typed error codes, timings, token counts, throughput, model load classification and fixed structured fields.

It excludes:

- prompts;
- generated output;
- arbitrary exception messages;
- private model paths;
- source document URIs;
- model bytes.

The Diagnostics UI maps only safe identifiers, digest prefixes and numeric metrics.

## Known limitation and follow-up

Telemetry does not survive process death in this iteration. Persistent cross-restart history requires a deliberate follow-up migration to `RoomTelemetryRepository`, including database lifecycle, storage reporting, retention verification and schema migration tests.
