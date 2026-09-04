# Host Control Plane unified observability

Status: active
Document type: feature-specification
Owner: shared-runtime-control-plane
Canonical scope: shared-runtime.control-plane.observability
Read when: implementing persistent session history, generation-to-session identity, runtime instrumentation or unified Sessions/Inference UI
Last reviewed: 2026-08-18

This workstream owns HCP-14 through HCP-16 and HCP-26. The invariant is that every generation executed by the single Harness runtime is observed at the runtime boundary, independent of whether it originates from Harness UI, device validation or a Binder consumer.

## HCP-14 — Session observability contract

Dependencies: HCP-0.

Introduce a privacy-safe `InferenceSessionRecord` carrying session ID, application ID, use-case ID, model digest, session kind, created/closed timestamps, explicit lifecycle status/close reason and optional preset/use-case/binding revision identity. Generation-run records gain session identity and relevant configuration revisions so runs can be joined to their owning session.

Lifecycle semantics cover active, normal close, cancellation, runtime failure and host-restart abandonment. Close reasons distinguish client request/disconnect, host shutdown/restart, model revocation, memory pressure and runtime failure.

Historical rows created before these fields existed may remain null after migration. Never fabricate missing revision/session identity.

Exit gate: every runtime-created session can be represented and every accepted generation can be related to its session/configuration identity.

## HCP-15 — Persistent Harness runtime history

Dependencies: HCP-14.

Extend the existing Room observability store with a bounded session table and generation session/revision columns using non-destructive migrations. Production Harness composition then moves from process-only `InMemoryTelemetryRepository` to the Room-backed repository while lightweight in-memory implementations remain for deterministic tests.

On process start, active session rows from the previous process are reconciled to an explicit abandoned-host-restart terminal state. Query surfaces support bounded filtering by application, use case, preset, model, status and date as needed by Harness UI.

Prompts, generated output, document content, private paths and unrestricted exception strings remain excluded.

Exit gate: internal/external session and generation history survives Harness restart with explicit restart reconciliation.

## HCP-16 — Runtime-first unified instrumentation

Dependencies: HCP-14. HCP-15 may proceed in parallel.

Instrument session creation/close and generation lifecycle at shared runtime/orchestration boundaries rather than Playground, Binder service or UI adapters. The runtime automatically records the application/use-case/session identity already present in accepted requests. Binding/use-case revisions are recorded only when the future resolved execution identity supplies them; they remain null before that integration.

Rejected requests without a valid session remain observable through bounded structured failure evidence but do not fabricate a session record. Telemetry failures remain best-effort and must never corrupt inference, cancellation or cleanup.

Exit gate: no supported path through the Harness runtime can execute an accepted inference while bypassing host telemetry.

## HCP-26 — Unified Sessions and Inferences UI

Dependencies: HCP-15/HCP-16.

Provide a host-wide sessions list covering Harness-internal and external applications. Filters include application, use case, preset, model, status and date where backed by repository queries. Session detail shows lifecycle, revision identity, model identity, close/failure reason and child inference metrics/timeline.

Persisted telemetry intentionally cannot show prompt/output/document text because those values are never stored. The UI must say unavailable where identity did not exist historically rather than inventing it.

Exit gate: Harness can answer which application executed which use case/preset, when, under which model/configuration identity, with what performance and terminal outcome.

## Required scenarios

- Harness Playground creates visible session/run history.
- External Binder inference creates the same record types with the consumer application ID.
- Multiple runs under one session are correctly joined.
- Host restart preserves prior terminal rows and reconciles stale active rows.
- Cancellation, client disconnect and runtime failure preserve explicit close reason.
- Telemetry-store failure does not convert a successful runtime operation into failure.
- Privacy sentinel data never appears in persisted records/logs.

## Focused validation

Run `observability:contracts` tests for schema/value invariants, `observability:room-store` JVM tests plus migration instrumentation assembly/execution where available, then `core:runtime-core` tests for session/run instrumentation. Switching phone composition to Room also requires the phone-test compile/lint/unit gate and repository-wide checks because storage/lifecycle fan out across domains. Physical cross-app execution remains a separate evidence gate.
