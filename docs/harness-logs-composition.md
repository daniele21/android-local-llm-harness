# Harness diagnostics logs composition

## Purpose

The connected phone-test application presents structured runtime logs and request timelines from the same process-scoped `TelemetryRepository` used by generation runs, health checks, resource snapshots, and benchmark baselines.

The log surface is observational. Opening Diagnostics, changing filters, or refreshing the screen never starts inference, health checks, resource capture, benchmark capture, model verification, or runtime preparation.

## Ownership

- `RuntimeTelemetry` produces structured, request-correlated events.
- `InMemoryTelemetryRepository` retains a bounded process-scoped log history.
- `HarnessLogSource` maps repository contracts into UI-independent state.
- `HarnessDiagnosticsLogsUi` renders filters, log entries, and request timelines.
- `MainActivity` temporarily owns filter and selected-request state until the planned ViewModel/UDF migration.

## Privacy boundary

The UI does not render the raw `StructuredLog.fields` map directly.

`HarnessLogSource` exposes only an allowlist of fields currently produced by `RuntimeTelemetry`:

- application and use-case identifiers;
- shortened model digest;
- queue position;
- fixed error code;
- timing metrics;
- token counts;
- cold/warm load classification;
- decode throughput.

Unknown fields are omitted. Prompt, output, private paths, document URIs, backend exception messages, stack traces, and model bytes are therefore not displayed even if an unsupported producer appends them to the repository.

Component names, event names, request identifiers, and field values are bounded before presentation. Full model digests are shortened.

## Filters

The bounded in-memory result supports:

- severity;
- component substring;
- event substring;
- request substring;
- search across the safe component, event, request identifier, field names, and field values.

The UI distinguishes:

- no logs recorded;
- no logs matching active filters;
- populated results;
- source unavailable.

## Request timeline

Run cards and request-correlated log entries can open a request timeline.

The timeline:

- queries the same repository by `RequestId`;
- includes the matching run terminal/current status when available;
- sorts events by timestamp and then original bounded repository order;
- uses the run start timestamp as the offset origin when available;
- otherwise uses the first correlated event;
- shows explicit empty and unavailable states.

No prompt or generated output is part of the timeline.

## Retention

The current connected iteration uses `InMemoryTelemetryRepository` with bounded retention. Logs and timelines are lost on process death. Cross-restart history requires the planned persistent telemetry decision and is not implied by the current UI.

## Validation

Deterministic tests cover:

- combined level, component, event, request, and safe-field filtering;
- chronological timeline ordering and offsets;
- model digest shortening;
- omission of prompt, output, exception-message, and private-path fields.

Android CI and physical-device validation remain required before the implementation is considered production-ready.
