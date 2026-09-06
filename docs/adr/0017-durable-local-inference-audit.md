# ADR 0017: Durable local inference audit is separate from normal telemetry

- Status: Accepted
- Date: 2026-09-04
- Supersedes: the prompt/output non-persistence restriction in ADR 0016 only for the explicit Harnex-owned local audit ledger described here

## Context

Harnex already records privacy-safe runtime telemetry: generation lifecycle, identifiers, timings, token counts, throughput, error codes, resource snapshots and structured logs. The phone application currently composes that telemetry in memory, and the durable `RoomTelemetryRepository` deliberately excludes prompts and generated output.

That model is insufficient for the product requirement that every inference accepted by Harnex be locally reconstructible after restart. A user must be able to open Harnex and answer: which application invoked the model, which input/effective prompt was executed, what answer/reasoning was produced, which model/configuration ran, how long it took and how it terminated.

Putting sensitive content into `StructuredLog` or `GenerationRunRecord` would weaken existing privacy-safe diagnostics/export semantics. Persisting it in the consumer application would also duplicate Harnex ownership and break the shared-runtime boundary. A distinct local audit domain is therefore required.

ADR 0016 previously prohibited persistent prompt/output content for durable shared-runtime jobs. This ADR intentionally narrows that rule: durable job metadata and normal telemetry remain content-free, while Harnex may persist inference content only through the explicit audit ledger below. Consumer applications such as RedactGuard do not gain permission to persist their own duplicate Harnex inference history.

## Decision

### Separate domains

Harnex has two observability domains with different trust and failure semantics:

```text
normal telemetry                         inference audit
---------------                          ---------------
privacy-safe                             sensitive local content
best-effort                              correctness gate for accepted inference
logs/runs/resources/benchmarks           caller + input/prompt + output + metrics
safe for diagnostics/export              never included in normal export
bounded retention                        bounded retention + explicit deletion
```

Normal `TelemetryRepository`, `GenerationRunRecord` and `StructuredLog` stay unchanged in purpose and continue to exclude prompt, document and generated-output content.

The audit domain owns a neutral `InferenceAuditRepository` contract. Android persistence, encryption and Room entities remain implementation details behind that contract. UI never reads Room entities directly.

### Correlation and ownership

`RequestId` is the stable correlation key between audit, generation telemetry and structured logs.

Each audit record contains:

- request ID;
- origin kind;
- application ID and use-case ID;
- verified package name for an external Binder consumer;
- received/prepared/running/completed timestamps as applicable;
- lifecycle status and typed terminal reason/error code;
- model/execution identity;
- original consumer/input content;
- effective prompt when it differs or is available after prompt planning;
- answer output and reasoning output;
- aggregate inference metrics required by the Activity detail view.

For external consumers, package identity comes only from the authenticated Binder `AuthorizedCaller`. The consumer cannot supply, override or spoof that field. UID and PID are authorization inputs but are not durable audit identity because they are not stable product identifiers.

### Strict admission semantics

An inference is not reported as normally accepted until its audit admission record has been durably committed.

Admission contains at least request identity, origin/application/use-case identity and original input. If the audit store or encryption key is unavailable, Harnex fails closed before scheduling model execution and returns a typed local audit/storage failure rather than silently running an untracked inference.

For an effective prompt that only exists after prompt planning, Harnex durably commits the prepared audit state before native decode begins. A failure to persist that prepared state prevents decode from starting.

### Strict terminal-success semantics

A successful model outcome is not reported to the caller as normal completion until the terminal audit record containing final answer/reasoning and aggregate metrics has been durably committed.

If terminal audit persistence fails after model execution, Harnex must not emit an ordinary successful completion. It surfaces a typed audit-persistence failure and marks the audit/storage health state degraded where possible. The generated content must not be silently presented as an unaudited success.

Runtime failures and cancellation remain failures/cancellation. Their terminal audit update is required where storage remains available; if storage has degraded after execution started, Harnex surfaces the audit/storage degradation explicitly and must not fabricate a complete record.

### Lifecycle states and restart reconciliation

The durable audit lifecycle is monotonic:

```text
ADMITTED -> PREPARED -> RUNNING -> COMPLETED
                              \-> FAILED
                              \-> CANCELLED
                              \-> INTERRUPTED
```

`PREPARED` may be skipped only when no separate prompt-planning phase exists. Terminal states never transition back to non-terminal states.

On Harnex process start, the audit owner queries bounded non-terminal rows. A row whose canonical runtime/logical job cannot truthfully continue is reconciled to `INTERRUPTED` with a stable safe reason such as host process loss. Restart never leaves stale `RUNNING` rows indefinitely and never claims native continuation across process death.

### Sensitive content handling

Sensitive fields are persisted only in the Harnex application sandbox. Input, effective prompt, answer and reasoning blobs are encrypted before Room persistence with an app-scoped AES-GCM key held by Android Keystore.

The design does not claim that the key is hardware-backed on every device. Hardware-backed properties require separate representative-device evidence.

Minimal query metadata may remain plaintext so bounded list/filter/reconciliation queries do not require decrypting every record. Plaintext metadata is restricted to identifiers, origin/package/application/use-case identity, status, timestamps, model digest/technical identity and aggregate numeric metrics. Sensitive content is never used as a plaintext index.

Android backup/export must not copy the audit database or key material. Normal diagnostics, reports, logs and telemetry exports remain content-free.

### Bounded retention and deletion

Audit persistence is explicitly bounded by policy. The first implementation supports independent limits for:

- maximum retained records;
- maximum record age;
- maximum encrypted sensitive-content bytes.

All limits must be positive and enforced at write/reconciliation boundaries. Retention removes oldest eligible terminal history first and never deletes canonical runtime/control-plane state.

Harnex exposes explicit clear-history behavior. Clearing audit history deletes audit records/content only; it does not remove models, control-plane applications, telemetry, settings or active runtime jobs. Active/non-terminal records are protected from ordinary retention/clear unless the operation first reaches a truthful terminal state.

### Coarse-grained writes

Audit writes occur at admission, meaningful prepared/running transitions and terminal completion/failure. Streaming token deltas remain bounded in memory. No token-by-token Room writes or generated-content event log is introduced.

### Entry-point completeness

Audit coverage is a Harnex invariant, not a RedactGuard-specific integration. Every generation path must be inventoried and either audited through the canonical owner or explicitly classified as non-generation. This includes:

- phone Playground/manual inference;
- authenticated Consumer/Binder generation;
- durable logical-job generation;
- evaluation execution;
- health-check generation where a model inference is actually executed.

A new inference entry point cannot bypass the audit owner merely because it is internal or diagnostic.

### UI boundary

The product surface is `Activity`/inference history, separate from technical Diagnostics.

Activity list queries use metadata summaries. Detail queries decrypt one selected record and show caller, input/prompt, output/reasoning, model/execution identity and metrics. A technical link may navigate from the same `requestId` to correlated Runs/Logs.

Compose depends on neutral presentation/source contracts, not Room, Keystore or Binder types.

## Consequences

- Harnex can provide durable local inference history without weakening privacy-safe telemetry/export semantics.
- Audit persistence becomes part of inference acceptance/completion correctness, while technical telemetry remains best-effort.
- Storage/key failure can block inference; the UI therefore needs explicit degraded/recovery states.
- The Host must propagate verified caller attribution into the canonical audit admission path without adding spoofable fields to public Consumer requests.
- Runtime/evaluation/health entry points must converge on one audit owner rather than adding local ad-hoc logging.
- Room migrations, retention and encryption lifecycle become security/privacy-sensitive implementation work and require STRONG validation.
- Process restart reconciliation becomes mandatory for non-terminal audit rows.
- RedactGuard remains a pure Consumer and does not own or duplicate Harnex audit persistence.

## Alternatives considered

### Put prompt/output into `StructuredLog`

Rejected because it would turn privacy-safe diagnostics into a sensitive content store and make filtering/export/reporting unsafe by default.

### Extend `GenerationRunRecord` with prompt/output

Rejected because telemetry has different failure semantics and broad existing consumers. Sensitive content should not fan out through every telemetry implementation/presenter.

### Let every consumer persist its own history

Rejected because caller identity, runtime metrics and execution truth belong to Harnex, and duplicate consumer stores would diverge under Binder/process loss.

### Keep content process-local and persist only metrics

Rejected because it cannot satisfy restart-visible prompt/result history or reconstruct what an accepted inference actually did.

### Persist every streaming delta

Rejected because it creates high write volume, content-log semantics and larger corruption/privacy surface without improving the final Activity use case.

### Make audit writes best-effort like telemetry

Rejected because it permits successful untracked inference, contradicting the explicit product invariant that accepted usage is auditable.

### Store plaintext content in app-private Room

Rejected because app sandboxing alone does not provide the intended defense-in-depth for locally retained sensitive prompts and outputs.

## Validation

Implementation is STRONG by default because it affects persistence, sensitive-data handling, runtime acceptance/completion semantics, Binder attribution and product UI.

Required deterministic evidence includes focused contract tests, Room schema/migration/retention tests, encryption/key-failure tests, runtime lifecycle tests, Host attribution/authorization tests, phone Activity presentation tests and an affected two-APK emulator journey. Deterministic Android gates unavailable locally are REMOTE_AUTOMATED.

Physical-device evidence is required only for claims that automation cannot establish, such as representative performance overhead, hardware-backed key properties, thermal behavior or OEM lifecycle behavior.
