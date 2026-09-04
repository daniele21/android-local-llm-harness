# Local inference activity and audit workstream

Status: active
Document type: workstream-state
Owner: runtime + android-service-host + apps/local-llm-phone-test
Canonical scope: workstream.local-inference-activity-audit
Read when: implementing durable attribution, content history, metrics correlation or Activity UI for Harnex inference
Last reviewed: 2026-09-04

Operational ledger: [`../current-state.md`](../current-state.md). Existing privacy-safe telemetry contract: [`../harness-telemetry-composition.md`](../harness-telemetry-composition.md). This file owns temporary sequencing, dependencies and write boundaries only.

## Goal

Make every inference accepted by Harnex locally reconstructible after restart: who invoked it, what input/effective prompt was executed, what answer/reasoning was produced, which model/configuration ran, how it performed and how it terminated.

The user-visible outcome is a source-backed inference Activity/history surface correlated to technical Runs/Logs by `requestId`.

## Non-goals and invariants

- Normal `TelemetryRepository`, `GenerationRunRecord` and `StructuredLog` remain privacy-safe; prompt/output content must not be added to normal telemetry or exported diagnostics.
- Sensitive inference history is a separate audit domain with separate repository/storage contracts.
- No cloud sync, analytics, automatic export, token-by-token Room writes or RedactGuard-owned copy of Harnex history.
- Every inference entry point must be inventoried; Playground, Consumer/Binder, evaluation/health generation and durable logical-job paths may not silently bypass audit.
- For external Consumers, package identity comes from authenticated `AuthorizedCaller`/Binder identity, never from caller-supplied display text.
- A generation may not be reported as normally accepted unless its audit admission record (identity + request + input) has been durably committed.
- Terminal success may not be reported as a normal success until terminal audit content/metrics have been durably committed. Storage failure must be explicit and fail closed; it must never become a silent untracked success.
- Process death/restart must reconcile non-terminal audit rows to an explicit interrupted/degraded terminal state when no canonical job can resume.
- Prompt/output/reasoning content is app-private, excluded from Android backup and encrypted at rest with an app-scoped Android Keystore key; query metadata remains minimal and plaintext only where needed.
- Retention is bounded by explicit policy (count/age/content budget) and supports an explicit clear-history action. Deletion removes content and associated searchable history without mutating runtime/control-plane truth.
- Persistence and encryption policy belong to the audit owner, not Compose, Binder parcels or RedactGuard.

## Execution DAG

| ID | State | Depends on | Owns / writes | Parallel with | Acceptance |
| --- | --- | --- | --- | --- | --- |
| LIA-00 | READY | — | architecture/ADR + feature contract only | — | Strict admission/terminal semantics, privacy boundary, lifecycle states, retention, encryption, query model and all inference entry points are explicit before code fans out. |
| LIA-05 | BLOCKED | LIA-00 | audit contracts + fakes/test fixtures only | — | Stable `InferenceAuditRepository`/record/query contracts exist without Room/Binder/UI types; `requestId` is the correlation key. |
| LIA-10 | BLOCKED | LIA-00 | `HarnessRuntimeGraph` telemetry composition + focused tests/docs | LIA-20, LIA-30, LIA-40, LIA-50 | Harnex uses existing `RoomTelemetryRepository`; Runs/Logs survive process restart with existing privacy-safe semantics and bounded retention. |
| LIA-20 | BLOCKED | LIA-05 | runtime-core audit lifecycle integration + unit tests | LIA-10, LIA-30, LIA-40, LIA-50 | Standard generation and evaluation cases admit input, capture effective prompt where available, persist terminal output/reasoning/metrics, and cannot bypass audit silently. |
| LIA-30 | BLOCKED | LIA-05 | android-service-host verified caller attribution + tests | LIA-10, LIA-20, LIA-40, LIA-50 | External runs bind `requestId` to kernel-derived package/application identity before runtime submission; spoofed/missing ownership fails closed. |
| LIA-40 | BLOCKED | LIA-05 | audit Room schema/repository, Keystore codec, migrations/retention/clear/restart tests | LIA-10, LIA-20, LIA-30, LIA-50 | Admission and terminal commits are ordered/transactional, content is encrypted at rest, retention is bounded, migrations are non-destructive and reads never expose ciphertext as content. |
| LIA-50 | BLOCKED | LIA-05 | Activity presentation models/routes/Compose/tests only; fake repository source | LIA-10, LIA-20, LIA-30, LIA-40 | Source-backed list/detail/filter/empty/error/degraded states show caller, status, model, input/output and metrics with progressive technical disclosure; no illustrative live values. |
| LIA-60 | BLOCKED | LIA-20, LIA-30, LIA-40, LIA-50 | Harnex composition root + real Activity source | — | One process-scoped audit owner is injected into runtime and Host; real Playground and Consumer runs appear in Activity and correlate to Runs/Logs without duplicate owners. |
| LIA-70 | BLOCKED | LIA-60 | restart reconciliation, storage-health/degradation, retention settings and clear-history integration | — | Restart never leaves stale RUNNING rows; storage/key failure is visible and blocks silent unaudited acceptance; clear/retention behave deterministically. |
| LIA-80 | BLOCKED | LIA-70 | automated cross-process evidence; RedactGuard test/evidence changes only if required | — | Two-APK flow proves RedactGuard inference -> verified identity -> prompt/input + output + metrics -> Harnex restart -> same durable record; failure/cancel/process-loss cases end truthfully. |
| LIA-90 | BLOCKED | LIA-80 | durable docs/current-state transfer + workstream cleanup | — | Canonical privacy/architecture/feature/runbook docs describe integrated behavior, STRONG exact-head automated gates are green and this temporary workstream can be deleted. |

## Parallel waves

**Wave 0 — contract convergence (sequential).** LIA-00 then LIA-05. Do not parallelize schema/runtime/UI implementation before the strict audit contract and privacy semantics are frozen; otherwise all tracks would rewrite the same boundary.

**Wave A — independent foundations (maximum safe parallelism).** Start LIA-10, LIA-20, LIA-30, LIA-40 and LIA-50 together after LIA-05. Their write ownership is intentionally disjoint: app telemetry composition, runtime lifecycle, Host attribution, audit persistence/encryption and UI presentation.

**Wave B — early convergence.** LIA-60 integrates those foundations onto one feature branch. Do not publish five stacked PRs merely because Wave A used parallel agent branches.

**Wave C — hardening.** LIA-70 follows composition because reconciliation and failure semantics require the real integrated store/runtime/Host path. UI polishing that does not touch lifecycle ownership may continue in parallel, but lifecycle fixes remain serialized through LIA-70.

**Wave D — proof.** LIA-80 is cross-repository evidence, not a RedactGuard feature rewrite. Production RedactGuard changes are out of scope unless the stable Consumer contract genuinely requires compatibility work.

## Key contract shape

The audit domain should expose a small neutral model equivalent to:

```text
requestId
origin/applicationId/useCaseId
verified package identity when external
received/started/completed timestamps
status + terminal reason/error
model/execution identity
consumer input + effective prompt (when distinct/available)
answer output + reasoning output
queue/load/planning/context/prefill/decode/TTFT/total timings
input/output/reasoning/answer token counts + throughput
```

Lifecycle writes are coarse-grained: admission, meaningful state transitions, terminal commit. Streaming deltas remain in bounded memory; no token-level database persistence.

## Validation strategy

Expected integration depth is **STRONG** because this work changes persistence, sensitive-data handling, runtime lifecycle, Binder attribution and product UI. FULL is not the default unless the repository selector escalates it.

ITERATION checks stay owner-scoped: audit contract/store unit tests, Room migration tests, runtime lifecycle tests, android-service-host authorization/attribution tests, phone-test presentation/navigation tests and existing telemetry-store tests. LIA-10 should prove restart persistence independently before audit-content work converges.

INTEGRATION must run the repository selector (`profile=auto`), all required deterministic STRONG gates on exact HEAD/base, Room AndroidTest APK assembly/migration coverage, phone-test compile/unit/lint/package checks and the affected two-APK emulator journey. If Android tooling is unavailable locally, deterministic Gradle/R8/Lint/E2E gates are `REMOTE_AUTOMATED`, not delegated to the user.

Physical ARM64/GGUF/thermal evidence is not required merely to prove audit persistence/identity/UI semantics. It becomes `REAL_ENVIRONMENT` only for claims about representative-device performance overhead, hardware-backed key properties or native/device behavior.

## Exit

The workstream closes only when an accepted RedactGuard inference and an internal Harnex inference both leave durable, queryable, correctly attributed terminal records with content and metrics; restart/process loss cannot silently erase or strand them; and normal telemetry remains privacy-safe and separately exportable.
