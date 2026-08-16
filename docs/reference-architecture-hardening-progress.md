# Local AI reference architecture hardening progress

Status: active
Document type: workstream-state
Owner: repository
Canonical scope: state.reference-architecture-hardening
Read when: determining current reference-architecture hardening status, dependencies or the next implementation slice
Last reviewed: 2026-08-16

Canonical target: [`reference-architecture-hardening-plan.md`](reference-architecture-hardening-plan.md)

This file owns concise workstream state. Repository-wide status remains in [`current-state.md`](current-state.md), capability outcomes in [`roadmap.md`](roadmap.md), and detailed target/dependencies in the canonical plan. Implementation slices start from the latest green `dev` and are refreshed when parallel dependencies land.

## Status legend

- `DONE`: target behavior, required automation and documentation are integrated.
- `PARTIAL`: meaningful target work exists, but implementation or evidence remains.
- `DEVICE`: automation is integrated; representative-device evidence remains.
- `PENDING`: focused target work has not started.

## Milestone status

| ID | Status | Current boundary | Next gate |
| --- | --- | --- | --- |
| RA-0 Architecture fitness | DONE | Canonical module inventory, dependency/native guards and normal JNI translation-unit linkage are integrated with zero verifier exceptions | Keep guards green |
| RA-1 Backend SPI/fake | DONE | `core:backend-spi`, store-neutral model source, llama.cpp adapter isolation and deterministic fake are integrated | Expand through RA-10 |
| RA-2 Runtime kernel | PARTIAL | `GenerationPlanningPolicy` is integrated as the first stateless extraction; session lifecycle ownership is now explicit, while model/context/resource ownership remains in `RuntimeOrchestrator` | Continue state-preserving decomposition only where ownership is already clear |
| RA-3 Lifecycle/state machines | PARTIAL | `SessionLifecycle` now authoritatively owns per-session request admission, close intent and release reservation; drain, idempotent close and release-retry behavior are integration-tested | Formalize remaining generation/cancellation transitions and converge with the separate residency lifecycle path without duplicating scheduler or residency ownership |
| RA-4 Failure/recovery | PARTIAL | Typed failures/recovery exist in several paths | Recovery matrix + deterministic fault/race injection after RA-3 |
| RA-5 Scheduling/backpressure | PARTIAL | Single decode, priority/FIFO, bounded queue admission/rejection, queued/running cancellation and scheduler close-race handling are integrated | Fairness/starvation, cancellation latency and slow/dead-consumer streaming semantics after RA-3 |
| RA-6 Shared runtime | PARTIAL | Negotiation, epochs, reconnect and client-death cleanup already exist | Operation idempotency/deadlines if required + real process-death evidence after RA-3/4/5 |
| RA-7 Observability | PARTIAL | Rich request-correlated runtime telemetry exists | Session/execution identity, cleanup/recovery/cancel latency and request-resource joins |
| RA-8 Device policy | PARTIAL | Compatibility plus memory/thermal evidence exists | Pure versioned execution planner after RA-7 evidence is reliable |
| RA-9 Execution identity | PARTIAL | Artifact, benchmark and evaluation identities already exist | Reconcile/version ownership; no parallel identity store |
| RA-10 Backend testkit | PARTIAL | Deterministic fake plus native/JNI/simulated tests exist | Shared conformance fixture with fake + llama.cpp consumers |
| RA-11 Security/provenance | PARTIAL | llama.cpp/wrapper pins, secure download, signing and privacy controls exist | Dependency verification, immutable action pins, SBOM/provenance in separate slices |
| RA-12 Certification | PENDING | Evidence infrastructure exists | Cumulative automated + representative-device certification |

## Closed baseline debt

The two violations that triggered tranche 1 are closed: `runtime-core -> backends:llama-cpp` was removed through RA-1 and [ADR 0014](adr/0014-backend-spi-boundary.md); the `llama_jni_entry.cpp -> #include "llama_jni.cpp"` pattern was removed through RA-0. `scripts/verify_architecture.py` now carries zero intended dependency or `.cpp`-include exceptions.

RA-0/RA-1 are stable baseline. RA-2 may continue internal decomposition. RA-3 now has authoritative per-session admission/close/release ownership, but must still formalize the remaining generation/cancellation lifecycle and converge with residency before RA-4/RA-5 behavior changes that depend on those transitions.

## Current parallel work

- **Architecture:** continue RA-2 only through state-preserving extractions; use the integrated RA-3 session lifecycle as the ownership boundary rather than creating a second mutable runtime owner.
- **Lifecycle:** extend RA-3 to the remaining generation/cancellation transitions while leaving decode queue authority in `SingleDecodeScheduler` and residency authority in the existing memory/residency path.
- **Quality:** define RA-10 fixture ownership only when the same conformance contract has fake and llama.cpp consumers.
- **Observability/identity:** make RA-7/RA-9 changes additive to existing stores/fingerprints.
- **Security:** handle dependency trust, workflow action trust and release provenance as separate RA-11 slices.
- **Memory:** continue residency/pressure/warm-idle and measured-cost work; context-memory admission is already wired before native context creation and Android memory observations are adapted at composition boundaries.

## Verified findings

### RA-2A — Runtime ownership

`GenerationPlanningPolicy` is integrated for effective configuration, output constraints, context sizing and reasoning control. `SessionLifecycle` now owns per-session request admission, close intent, active-request drain and release reservation. `RuntimeOrchestrator` still owns the session registry, loaded model/context handles, physical resource release, memory-pressure coordination and generation orchestration. Further RA-2 extraction must preserve those ownership boundaries until the corresponding RA-3 state is explicit.

### RA-3B — Authoritative session lifecycle

The previous `SessionDescriptor.activeRequests`, `closing` and `released` atomics have been replaced by one `SessionLifecycle`. `generate()` acquires through the lifecycle; `closeSession()`, runtime shutdown and critical-memory cleanup converge on the same close/release transitions; terminal request cleanup drains through `releaseRequest()`; physical release is reserved once through `tryBeginRelease()`. If physical context release fails, the lifecycle rolls back from `RELEASING` to `CLOSING` so cleanup can be retried without losing the session. Integration tests cover close-during-generation, rejection of new work after close intent, exactly-once release after drain and release-failure retry.

This closes duplicate session close/request/release ownership, not RA-3 as a whole. `SingleDecodeScheduler` remains authoritative for queued/active decode state, and the memory/residency path remains authoritative for residency. The next lifecycle slice must formalize only semantics that are not already owned by those components.

### RA-5A — Scheduler

`SingleDecodeScheduler` already enforces one active decode, priority with FIFO sequencing, bounded waiting admission with deterministic rejection, queued/running cancellation, `cancelAll()` and close. The scheduler close path has regression coverage for the concurrent queue-drain race. RA-5 therefore no longer needs a basic capacity primitive; remaining work is fairness/starvation policy, queued/active cancellation latency evidence, and bounded streaming/slow-or-dead-consumer backpressure semantics. Simultaneous decode remains deferred.

### RA-6A — Shared runtime

Binder already provides major/minor and feature negotiation, typed incompatibility, connection epochs, typed connection loss, explicit reconnect and idempotent `connect()`/`close()`. The service host links clients to death and cleans requests, sessions, consumers, death links, dispatchers and ledger state. Remaining work is operation-level replay/idempotency only if needed, deadlines where justified, and real service-process death/reconnect evidence after RA-3/4/5.

### RA-7A / RA-9A — Telemetry and identity

Telemetry already records request/app/use-case/model identity, queue/load/TTFT/prefill/decode/total timings, tokens/throughput, effective generation settings, context/prompt/template data and stop/planning timings. Missing correlation is mainly session/execution identity, cleanup/unload/recovery, cancellation latency and request-to-resource joins.

Identity already exists at artifact, benchmark and evaluation semantic/runtime/run levels. RA-9 must reconcile/version these contracts; a general execution identity must be additive and reused rather than becoming a fourth store.

### RA-10A / RA-11A — Testkit and supply chain

The deterministic fake already supports controlled failures, blocking, streaming, cancellation and release accounting, but no established shared test-fixture convention was found. Do not create a nominal testkit module without two real consumers.

The Gradle wrapper checksum and llama.cpp revision are pinned. Verified remaining supply-chain gaps are missing Gradle dependency-verification metadata, mutable major-version GitHub Action references and no current SBOM/provenance artifact.

## Update rule

Update this tracker with state/blocker/next-slice changes; update `current-state.md` only for repository-wide state, `roadmap.md` only for capability outcomes, and architecture/ADRs only for durable boundaries. A milestone becomes `DONE` only after integration, targeted regression coverage, required docs, cumulative green `dev` and any required device evidence.
