# Local AI reference architecture hardening progress

Status: active
Document type: workstream-state
Owner: repository
Canonical scope: state.reference-architecture-hardening
Read when: determining current reference-architecture hardening status, dependencies or the next implementation slice
Last reviewed: 2026-08-16

Canonical target: [`reference-architecture-hardening-plan.md`](reference-architecture-hardening-plan.md)

This tracker owns concise workstream state only. Repository-wide baseline/blockers/next work remain in [`current-state.md`](current-state.md); capability outcomes remain in [`roadmap.md`](roadmap.md). Every implementation slice starts from the latest green `dev` available when it begins and must be replayed/refreshed before merge when a dependency lands in parallel.

## Status legend

- `DONE`: target behavior, automated acceptance and required evidence/docs are integrated.
- `PARTIAL`: meaningful target work exists, but architecture, tests or evidence remain.
- `DEVICE`: implementation/automation are integrated; representative device evidence remains.
- `PENDING`: focused target work has not started.

## Milestone status

| ID | Status | Current boundary | Remaining gate | Depends on |
| --- | --- | --- | --- | --- |
| RA-0 Architecture fitness | DONE | Canonical Gradle inventory, executable dependency/native guards and normal JNI translation-unit linkage are integrated; verifier carries zero debt exceptions | Keep rules green as later modules/source boundaries change | None |
| RA-1 Backend SPI/fake | DONE | `core:backend-spi`, store-neutral `BackendModelSource`, llama.cpp adapter isolation and deterministic fake are integrated; runtime-core has no concrete backend dependency | Expand into RA-10 conformance coverage as lifecycle/failure semantics mature | RA-0 |
| RA-2 Runtime kernel | PARTIAL | RA-2A mapped state ownership; first stateless generation-planning extraction is implemented and validated while all mutable lifecycle state remains in `RuntimeOrchestrator` | Integrate the validated slice, then choose the next stateless/coordinator extraction without creating a second lifecycle owner | RA-0, RA-1 |
| RA-3 Lifecycle/state machines | PARTIAL | load/generate/cancel/release/shutdown and pressure behavior exist, with one mutable runtime owner retained through RA-2 | Formal code/test transition tables, ownership matrix, idempotent terminals and one residency path | RA-2 state-owner boundary |
| RA-4 Failure/recovery | PARTIAL | Typed privacy-safe failures/recovery exist in several paths | Cross-layer recovery matrix + deterministic fault/race injection | RA-1, RA-3 |
| RA-5 Scheduling/backpressure | PARTIAL | Single active decode, priority/FIFO sequencing and queued/running cancellation exist | Replace unbounded waiting admission with explicit capacity/rejection; then cover fairness, slow-consumer/backpressure and cancellation latency | RA-3 |
| RA-6 Shared runtime | PARTIAL | Version/capability negotiation, connection epochs, explicit reconnect, client-death cleanup and idempotent connect/close are already implemented/tested | Define operation-level idempotency/deadlines if required and add real service-process death/reconnect/resource-cleanup evidence after RA-3/4/5 | RA-3/4/5 |
| RA-7 Observability | PARTIAL | Request-correlated telemetry covers queue, prepare, TTFT/prefill/decode/total and effective generation metadata | Add only missing lifecycle/recovery correlation: session/execution identity, cleanup/unload/recovery/cancel latency and request-resource joins | RA-0; continuous |
| RA-8 Device policy | PARTIAL | Compatibility, profiles, memory/thermal capture and tuning work exist | Pure versioned planner using validated device/model/resource inputs | RA-7 + evidence |
| RA-9 Execution identity | PARTIAL | Artifact identity, benchmark execution fingerprints and rich evaluation semantic/runtime identities already exist | Reconcile overlapping identity ownership/versioning before introducing any shared execution identity; no parallel identity store | RA-0 |
| RA-10 Backend testkit | PARTIAL | Deterministic SPI fake plus native/JNI/simulated acceptance are integrated | Choose a genuinely reusable fixture owner and run one conformance suite against fake and llama.cpp; expand later with RA-3/4/5 cases | RA-1 |
| RA-11 Security/provenance | PARTIAL | llama.cpp pin, Gradle wrapper checksum, secure download, signing/versioning, packaging and privacy rules exist | Gradle dependency verification, immutable CI action pinning and SBOM/provenance/release metadata in separate reviewable slices | RA-0 |
| RA-12 Certification | PENDING | Existing evidence tooling covers several gates | Cumulative architecture/resilience/packaging/device evidence | Applicable RA-1..11 |

## Closed baseline architecture debt

The two concrete violations that triggered the first tranche are closed and regression-guarded:

- `runtime-core -> backends:llama-cpp` was removed through RA-1 and the backend boundary recorded in [ADR 0014](adr/0014-backend-spi-boundary.md);
- `llama_jni_entry.cpp -> #include "llama_jni.cpp"` was removed through RA-0 by deleting the redundant entry source and compiling the JNI implementation normally.

`scripts/verify_architecture.py` now carries zero intended dependency exceptions and zero intended `.cpp`-include exceptions. Any recurrence must fail CI rather than become implicit debt.

## Dependency routing

```text
RA-0 DONE -> RA-1 DONE -> RA-10
              \-> RA-4 <- RA-3
RA-2 ----------------> RA-3 -> RA-5
                         \-> memory/residency integration
RA-7 -> RA-8
RA-9
RA-11
RA-3 + RA-4 + RA-5 -> RA-6
applicable RA-1..11 -> RA-12
```

RA-0 and RA-1 are stable baseline. RA-2 may continue internal decomposition, but RA-3 is the point where mutable lifecycle/state semantics become explicit; RA-4/RA-5 behavior changes should consume those semantics rather than race ahead of them.

## Parallel lanes

| Lane | Current work | Integration constraint |
| --- | --- | --- |
| A Architecture core | Integrate first RA-2 planning slice; identify next state-preserving extraction; prepare RA-3 transition/ownership model | Do not create a second mutable runtime owner |
| B Quality/resilience | Design RA-10 fixture ownership on the integrated SPI | RA-4/5 cases later consume RA-3 semantics |
| C Observability/device | RA-7 additive correlation design; RA-8 planner design can follow | Measured RA-8 waits for reliable resource/device evidence |
| D Identity/security | RA-9 identity reconciliation + RA-11 supply-chain slices | Reuse existing owners/stores and version fingerprints explicitly |
| E Shared runtime | RA-6 gap list is narrowed; no protocol refactor now | Behavior changes wait for RA-3/4/5 |
| Existing memory | Residency/pressure/warm-idle continues | Converge through RA-3/5/7/8 |

## Current implementation block

1. **RA-2 first extraction:** integrate validated stateless generation planning while keeping session/model/context/scheduler/memory ownership in `RuntimeOrchestrator`.
2. **RA-2/RA-3 boundary:** map the next extraction against explicit model/session/generation/resource transitions before moving mutable ownership.
3. **RA-10 seed:** choose a reusable conformance-fixture boundary that can exercise both deterministic fake and llama.cpp rather than creating a nominal testkit used by one module.
4. **RA-7/RA-9 reconciliation:** design the minimum additive correlation/execution identity changes against existing observability, benchmark and evaluation identities before persistence changes.
5. **RA-11 supply chain:** add dependency verification, immutable action references and provenance independently so each trust boundary remains reviewable.

## Verified discovery and implementation outcomes

### RA-2A — Runtime ownership and first extraction

`RuntimeOrchestrator` owns mutable runtime state, session descriptors, scheduler coordination, loaded model/context ownership, integrity-cache integration, deferred unload state, memory-pressure behavior and generation execution. Its generation path also owned configuration resolution/validation, output constraints, reasoning control and context-size selection.

The first implementation slice therefore extracts only **stateless generation planning** into `GenerationPlanningPolicy`: effective generation configuration, output-constraint support, context-size choice and reasoning-control derivation. Focused pure tests cover seed resolution, request overrides, raw-completion/thinking rejection, output constraints, auto context sizing and reasoning support. Session acquisition/release, model residency, context materialization, scheduler mutation, cancellation, memory pressure and terminal cleanup remain in one authoritative runtime owner.

The next RA-2 slice must preserve that property. Prefer another stateless/coordinator responsibility before any SessionManager-style state split; RA-3 should first formalize lifecycle ownership and legal transitions.

### RA-5A — Scheduler gap confirmed

`SingleDecodeScheduler` already provides one active decode, explicit priorities, FIFO ordering inside a priority, queued/running cancellation, `cancelAll()` and close behavior. Its waiting structure is currently an unbounded `PriorityBlockingQueue`, so queue admission is not bounded. RA-5 should add explicit capacity and typed admission/rejection after RA-3 stabilizes lifecycle semantics, then cover fairness/starvation, cancellation latency and stream/consumer backpressure without enabling simultaneous decode.

### RA-6A — Shared-runtime gap narrowed

The Binder boundary already has major/minor protocol-range negotiation, feature negotiation and closed incompatibility behavior. The client has explicit connection states, connection epochs that ignore stale callbacks, typed connection loss, idempotent `connect()`/`close()` and tested explicit reconnect. The service host links client binders to death and cleans requests, sessions, consumers, death links, dispatchers and ledger state when a client dies.

Therefore RA-6 must not rebuild these foundations. Remaining hardening is narrower: decide whether duplicate external operations need replay/idempotency semantics instead of current rejection, propagate deadlines where a real requirement exists, and add end-to-end evidence for actual service-process death/reconnect/resource cleanup after RA-3/4/5 settle runtime semantics.

### RA-7A — Existing telemetry versus missing correlation

Existing telemetry records request ID, application/use-case, model digest, queue/load/TTFT/prefill/decode/total timing, tokens/throughput, effective preset and sampling values, context/prompt size, chat-template identity, stop reason and planning/context-creation timings. Missing target coverage is narrower than initially assumed: session/execution identity correlation, explicit cleanup/unload/recovery phases, cancellation latency and a defined request/run-to-resource-snapshot join. Extend existing owners; do not create another telemetry store.

### RA-9A — Identity overlap to reconcile

The repository already has immutable model-artifact digest, `BenchmarkExecutionIdentity`, and evaluation semantic/runtime/run fingerprints. Evaluation already carries model profile/quantization, backend revision, device/API/ABI, harness build identity, runtime tuning profile, load/warmup policy and semantic generation settings. RA-9 must first define ownership/versioning between these layers; silently changing fingerprint composition would invalidate comparisons. Any general runtime execution identity should be additive/versioned and reused by benchmark/evaluation rather than creating a fourth competing store.

### RA-10A — Testkit ownership constraint

The integrated deterministic runtime fake already supports controlled load/context/generation failure, blocking, streaming, cancellation and release accounting. The repository does not currently expose an obvious shared `testFixtures`/testkit convention. Do not create a new module merely because the target names a testkit: first define a fixture boundary with at least two real consumers, then make fake and llama.cpp conformance use the same contract suite.

### RA-11A — Supply-chain baseline

The Gradle wrapper distribution is SHA-256 pinned and the llama.cpp revision has an explicit repository guard. Verified gaps include absence of Gradle dependency-verification metadata, mutable major-version GitHub Action references in workflows, and no current SBOM/provenance artifact found in the repository. Address dependency trust, workflow trust and release provenance independently so each change remains auditable and reversible.

## Memory coordination

Current memory work continues: keep one residency owner; represent unload causes explicitly; let scheduler state become authoritative for idle decisions; use existing resource/telemetry owners; treat device PSS/thermal data as evidence rather than universal thresholds; migrate through RA-3/RA-5 contracts without unnecessary user-visible change.

## Update and completion rule

On status/blocker/dependency/next-slice change, update this tracker in the same change. Update `current-state.md` only for repository-wide operational state, `roadmap.md` only for capability outcomes, the target only for intent/acceptance changes, and `architecture.md`/ADR only for durable boundaries/decisions.

A milestone is `DONE` only after integration into `dev`, targeted acceptance, applicable regression guards, failure/cancel/cleanup coverage, required docs, cumulative green `dev`, and required physical evidence. Use `DEVICE` when implementation is integrated but representative hardware evidence remains. The workstream closes only after RA-12 removes/owns all P0 exceptions and demonstrates the reference-grade properties.
