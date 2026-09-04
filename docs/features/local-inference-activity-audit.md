# Local inference Activity and audit

Status: active
Document type: feature-specification
Owner: runtime + android-service-host + apps/local-llm-phone-test
Canonical scope: feature.local-inference-activity-audit
Read when: changing inference attribution, audit persistence, Activity history, retention or correlation with diagnostics
Last reviewed: 2026-09-04

Architecture decision: [`../adr/0017-durable-local-inference-audit.md`](../adr/0017-durable-local-inference-audit.md). Temporary execution state: [`../workstreams/local-inference-activity-audit.md`](../workstreams/local-inference-activity-audit.md).

## Product outcome

Harnex keeps a local, bounded and restart-safe history of every inference it accepts. The user can see who invoked the model, what was executed, what was produced and how the inference performed without reading low-level logs.

`Activity` answers the product question “who used local AI and what happened?”. `Diagnostics` answers the technical question “why did runtime behavior succeed or fail?”. Both correlate through `requestId` but keep separate storage/privacy semantics.

## User journey

```text
Consumer/Playground starts inference
        |
        v
Harnex authenticates / resolves origin
        |
        v
Durable audit admission succeeds
        |
        v
Prompt planning -> durable prepared state
        |
        v
Runtime decode
        |
        v
Durable terminal audit commit
        |
        +----> Activity list/detail
        |
        +----> privacy-safe Runs/Logs by requestId
```

The minimum external-consumer acceptance journey is:

1. RedactGuard starts a real Consumer inference.
2. Harnex records the verified consumer package/application/use case before execution.
3. The final Activity row shows completed/failed/cancelled/interrupted truthfully.
4. The detail shows input/effective prompt when available, answer/reasoning, model/execution identity and inference metrics.
5. Harnex process/app restart preserves the same record.
6. The same `requestId` opens correlated technical evidence.

## Domain boundaries

### Normal telemetry

`TelemetryRepository` remains content-free and best-effort. It owns lifecycle/performance evidence that is safe for diagnostics and export:

- request/application/use-case/model identifiers;
- status/error codes;
- timings/token counts/throughput;
- structured safe fields;
- health/resource/benchmark evidence.

It never becomes the prompt/output store.

### Inference audit

`InferenceAuditRepository` owns sensitive local history and strict commit semantics. Its durable record contains:

- origin and verified caller identity;
- original input;
- effective prompt after prompt planning when available;
- output and reasoning;
- execution/model identity;
- aggregate metrics;
- lifecycle/terminal state.

Audit content is local-only and never part of normal reports or logs.

### Consumer application

A Consumer such as RedactGuard owns its product workflow and process-local/document state. It does not persist a duplicate Harnex audit record and does not declare its own verified package identity.

## Lifecycle contract

Supported statuses:

```text
ADMITTED
PREPARED
RUNNING
COMPLETED
FAILED
CANCELLED
INTERRUPTED
```

`COMPLETED`, `FAILED`, `CANCELLED` and `INTERRUPTED` are terminal.

Required ordering:

- admission must durably exist before generation is accepted/scheduled;
- prepared state must durably contain the effective prompt before native decode when prompt planning produces one;
- successful terminal content/metrics must durably commit before ordinary completion is emitted;
- terminal states never regress;
- restart reconciliation converts orphaned non-terminal rows to `INTERRUPTED` rather than leaving stale running state.

Storage or encryption failure is a first-class typed failure. Harnex must not silently fall back to unaudited inference.

## Attribution

Origin kinds distinguish internal/product/system sources without trusting caller-supplied labels:

- `HARNEX_INTERNAL` — phone Playground/manual Harnex generation;
- `EXTERNAL_CONSUMER` — authenticated Binder consumer;
- `EVALUATION` — model evaluation execution;
- `HEALTH_CHECK` — health generation that actually invokes the model.

Every record has `applicationId` and `useCaseId`. External records additionally require a verified package name from `AuthorizedCaller`.

UID/PID may participate in live authorization but are not persisted as product identity. Display names/icons are presentation lookups and not audit truth.

## Content model and bounds

The audit model preserves the original input kind (`TEXT`, `MESSAGES`, `RAW_COMPLETION`) and a bounded canonical content representation suitable for local detail display. Effective prompt, answer and reasoning are separate sensitive fields.

Contract bounds apply before persistence so a repository implementation cannot receive unbounded text. Streaming deltas remain process memory only; the store receives coarse lifecycle snapshots rather than token-level events.

The first persistent implementation uses encrypted sensitive blobs. Metadata required for list/filter/reconciliation remains minimal plaintext.

## Metrics and execution identity

The Activity detail must be able to show source-backed values when available:

- model digest/identity;
- model load kind;
- preset/configuration identity;
- backend ID/revision/fingerprint/placement where available;
- queue time;
- model load time;
- prompt planning/context creation time;
- prefill/decode time;
- TTFT and time-to-first-answer;
- total time;
- input/output/reasoning/answer token counts;
- decode tokens/second;
- stop reason;
- safe terminal error/reason.

Unavailable values remain unavailable; the UI must not derive or fabricate them.

## Query model

List queries are bounded and metadata-oriented. They support at least:

- newest-first pagination/windowing;
- application filter;
- use-case filter;
- status filter;
- time cutoff/window.

List results are summaries and do not require decrypting every prompt/output. Full sensitive content is read only for an explicitly opened detail record.

Reconciliation has a dedicated bounded non-terminal query. Full-table unbounded scans are not part of the contract.

## Persistence and encryption

The Android implementation uses app-private Room persistence with sensitive content encrypted before it reaches Room. The key is app-scoped and generated/held through Android Keystore using AES-GCM.

The design does not depend on hardware-backed Keystore availability. Key unavailability/corruption is surfaced as audit storage degradation and requires explicit recovery; it never produces plaintext fallback.

The audit store is excluded from Android backup/export. Normal diagnostics export never includes decrypted audit content.

## Retention and clear history

Retention has independent bounds for record count, age and encrypted-content bytes. Oldest terminal history is evicted first. Non-terminal records are protected until reconciled to truthful terminal state.

Clear history is explicit and scoped only to audit history. It does not mutate:

- active runtime jobs;
- model files;
- control-plane state;
- telemetry Runs/Logs;
- application assignments/presets;
- user settings unrelated to audit retention.

If active records exist, clear either preserves them or requires deterministic reconciliation before removal; it never silently deletes evidence for work still reported as running.

## Activity information architecture

Activity is a first-class product surface rather than a Logs subsection.

### List

Each row prioritizes:

1. application/origin;
2. terminal/current status;
3. use case;
4. timestamp;
5. concise performance summary such as total latency and throughput when available.

Filters use application, status and period/use case without exposing technical IDs by default.

### Detail

Progressive disclosure order:

1. **Usage** — caller/application, use case, time, status.
2. **Input** — original input and effective prompt when different/available.
3. **Output** — answer and reasoning where applicable.
4. **Performance** — latency/token/throughput metrics.
5. **Technical** — request ID, model digest, backend/configuration identity, terminal reason.
6. **Open technical timeline** — navigate to correlated Runs/Logs.

Empty, loading, unavailable, degraded-store, decrypt-failure and deleted/retained-away states are explicit. Opening Activity is observational and must not load a model, start inference or mutate retention.

## Entry-point inventory

The implementation must cover all current model-generation owners:

| Entry point | Canonical generation owner | Audit origin |
| --- | --- | --- |
| Phone Playground/manual inference | phone controller -> runtime client | `HARNEX_INTERNAL` |
| Consumer SDK/Binder generation | authenticated Host -> Consumer facade/runtime | `EXTERNAL_CONSUMER` |
| Durable logical Consumer jobs | Host logical-job owner -> Consumer facade/runtime | `EXTERNAL_CONSUMER` |
| Evaluation generation/batch | evaluation runtime adapter/runtime | `EVALUATION` |
| Generation sanity health check | health engine/client/runtime | `HEALTH_CHECK` |

Adding a new generation path requires updating this table and demonstrating that it reaches the canonical audit owner.

## Failure and recovery states

The feature distinguishes at least:

- admission store unavailable;
- encryption key unavailable/corrupt;
- prepared-state persistence failure;
- terminal persistence failure after model execution;
- model/runtime failure;
- explicit cancellation;
- critical-memory interruption;
- host process loss/restart interruption;
- record retained away or explicitly cleared;
- content decrypt failure with metadata still queryable.

No state is converted to normal success merely because inference output happened to exist in process memory.

## Validation

Repository integration is STRONG because the feature spans sensitive persistence, runtime lifecycle, Binder identity and product UI.

Focused iteration evidence belongs beside each owner. Integration evidence must include:

- audit contract invariants and redacted `toString` behavior;
- Room migrations/retention/ordering;
- AES-GCM round-trip and key/storage failure behavior;
- runtime acceptance/prepared/completion ordering;
- Binder verified-caller attribution and spoof rejection;
- Activity list/detail/filter/degraded-state presentation;
- restart reconciliation;
- two-APK RedactGuard -> Harnex durable Activity record with cancel/failure/process-loss cases.

Physical ARM64/GGUF/thermal evidence is not required merely to prove audit correctness. Representative overhead or hardware-backed-key claims require separate real-environment evidence.
