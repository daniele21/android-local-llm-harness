# Harness connected telemetry composition

Status: active
Document type: feature-specification
Owner: apps/local-llm-phone-test
Canonical scope: phone.diagnostics.telemetry
Read when: changing connected-app telemetry assembly, retention or timeline presentation
Last reviewed: 2026-09-04

## Decision

Harnex uses one process-scoped `RoomTelemetryRepository` owned by `HarnessRuntimeGraph` for normal privacy-safe telemetry. The repository lives in the Harnex application sandbox as `harnex-telemetry.db` and uses the existing Room telemetry schema/migrations.

The same repository is injected into the single shared `RuntimeOrchestrator` used by Playground, authenticated Consumer execution and physical-device validation. Diagnostics reads through `HarnessDiagnosticsSource`; it does not create a parallel runtime, model store, telemetry store or registry.

This is distinct from the sensitive inference-audit domain defined by [ADR 0017](adr/0017-durable-local-inference-audit.md). Prompt, effective prompt, reasoning and generated output remain forbidden from normal telemetry even though the new audit ledger may persist them under its separate encryption/failure policy.

## Rationale

Persistent normal telemetry is now required because runtime evidence must remain available after ordinary Harnex UI recreation and process restart. The existing `RoomTelemetryRepository` already owns the required behavior:

- stable `TelemetryRepository` contracts;
- serialized off-main-thread writes and ordered query barriers;
- lifecycle upsert by request ID;
- bounded independent retention;
- Room migrations and schema validation;
- best-effort write isolation from inference behavior.

The app keeps `observability:in-memory-store` as a test/fake dependency; it is no longer the production runtime-graph telemetry owner.

## Retention

The connected graph retains at most:

- 200 generation runs;
- 1,000 structured logs;
- 200 resource snapshots.

Diagnostics uses smaller bounded query windows for presentation. Benchmark/health retention continues to follow the stable telemetry repository contract and existing store policy.

## Lifecycle

- Constructing `HarnessRuntimeGraph` opens the process-scoped telemetry database but does not load llama.cpp or a GGUF model.
- The runtime is created lazily when an operation needs it and receives the already-owned telemetry repository.
- Replacing or removing the active model closes the runtime but does not clear telemetry history.
- Activity recreation does not own or reopen a parallel repository.
- The shared-runtime Service and Harnex UI resolve the same `HarnessRuntimeGraph` and therefore the same telemetry owner.
- `HarnessRuntimeGraph.close()` closes runtime ownership first, then drains/closes the telemetry repository and finally closes the control-plane store.
- Android process restart reopens the same app-private telemetry database, so retained Runs/Logs remain queryable.

## Privacy and failure semantics

Normal telemetry includes identifiers, lifecycle status, typed error codes, timings, token counts, throughput, model/configuration execution identity and fixed structured fields.

It excludes:

- prompts and effective prompts;
- generated answer/reasoning content;
- arbitrary exception messages;
- private model paths;
- source document URIs;
- model bytes.

The Diagnostics UI maps only source-backed safe identifiers, digest prefixes and numeric metrics.

Normal telemetry remains best-effort: lifecycle write failures inside `RoomTelemetryRepository` are isolated from inference success/cancellation. This is deliberately weaker than ADR-0017 audit persistence, which becomes a correctness gate for accepted/normal-success inference once its production composition is integrated.

## Verification

Changes to this composition require the existing Room repository unit/migration coverage plus phone-test compile/unit/lint/package checks selected by repository scope. Cross-restart product evidence belongs to the local-inference Activity/audit workstream when the complete runtime + audit + UI path is composed; this document does not promote emulator evidence into physical performance or hardware claims.
