# Harness embedded health composition

Status: active
Document type: feature-specification
Owner: apps/local-llm-phone-test
Canonical scope: phone.diagnostics.health
Read when: changing connected-app health checks, composition or presentation
Last reviewed: 2026-08-06

## Scope

The Play-installable Harness application exposes an explicit, non-destructive health suite over the embedded runtime graph. The suite uses the existing `HealthEngine` and writes results to the same process-scoped telemetry repository used by generation observability.

## Ownership

`MainActivity` creates one `HarnessHealthSource` using capabilities owned by `HarnessRuntimeGraph`:

- the shared `FileSystemModelStore`;
- the shared `TelemetryRepository`;
- a selected-model supplier;
- a runtime-state supplier.

Health execution runs on one dedicated executor owned by the Activity. Destroying the Activity shuts down that executor. The health source does not create a second runtime, model store or telemetry registry.

## Checks

The first connected suite contains:

- `model.selected`: reports `WARN` when no GGUF is selected;
- `model.integrity`: verifies the selected digest against the stored artifact, or reports `NOT_RUN` without a selected model;
- `runtime.state`: reports `FAIL` only when the embedded runtime is in `FAILED`, and `NOT_RUN` before runtime creation;
- `telemetry.repository`: verifies that bounded telemetry queries are readable.

Generation sanity is intentionally not executed by ordinary health refresh because it starts inference. It remains available through the explicit physical-device validation workflow.

## Execution semantics

Health execution is user initiated. Navigation and diagnostics refresh are observational and never start checks. The UI disables health execution while model import, Playground generation, physical validation or another health suite is active.

Results are persisted through `TelemetryRepository.saveHealth`. The visible overall state uses the same worst-status ordering as `HealthSuiteReport`:

1. `FAIL`;
2. `WARN`;
3. `NOT_RUN` when no actionable check ran;
4. `PASS` otherwise.

## Privacy

Health details are fixed, structured messages. They do not include:

- model backing paths;
- model file names;
- full model digests;
- prompt text;
- generated output;
- arbitrary backend exception messages.

Unexpected check exceptions are converted by `HealthEngine` to a fixed failure detail.

## Current limitations

- health history is process-memory-only because the connected app currently uses `InMemoryTelemetryRepository`;
- the UI exposes run-all only; targeted actions remain a future detail-route capability;
- host unit tests do not replace Android build, emulator or physical-device validation;
- model integrity can be I/O intensive, so it must remain off the main thread.
