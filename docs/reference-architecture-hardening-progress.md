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
| RA-2 Runtime kernel | PARTIAL | First stateless generation-planning extraction is implemented/validated; mutable lifecycle state remains in `RuntimeOrchestrator` | Integrate slice; choose next state-preserving extraction |
| RA-3 Lifecycle/state machines | PARTIAL | Lifecycle behavior exists under one mutable owner | Formal transitions, ownership, idempotent terminals and residency path |
| RA-4 Failure/recovery | PARTIAL | Typed failures/recovery exist in several paths | Recovery matrix + deterministic fault/race injection after RA-3 |
| RA-5 Scheduling/backpressure | PARTIAL | Single decode, priority/FIFO and cancellation exist | Bounded admission/rejection, fairness, slow-consumer and cancellation-latency semantics after RA-3 |
| RA-6 Shared runtime | PARTIAL | Negotiation, epochs, reconnect and client-death cleanup already exist | Operation idempotency/deadlines if required + real process-death evidence after RA-3/4/5 |
| RA-7 Observability | PARTIAL | Rich request-correlated runtime telemetry exists | Session/execution identity, cleanup/recovery/cancel latency and request-resource joins |
| RA-8 Device policy | PARTIAL | Compatibility plus memory/thermal evidence exists | Pure versioned execution planner after RA-7 evidence is reliable |
| RA-9 Execution identity | PARTIAL | Artifact, benchmark and evaluation identities already exist | Reconcile/version ownership; no parallel identity store |
| RA-10 Backend testkit | PARTIAL | Deterministic fake plus native/JNI/simulated tests exist | Shared conformance fixture with fake + llama.cpp consumers |
| RA-11 Security/provenance | PARTIAL | llama.cpp/wrapper pins, secure download, signing and privacy controls exist | Dependency verification, immutable action pins, SBOM/provenance in separate slices |
| RA-12 Certification | PENDING | Evidence infrastructure exists | Cumulative automated + representative-device certification |

## Closed baseline debt

The two violations that triggered tranche 1 are closed: `runtime-core -> backends:llama-cpp` was removed through RA-1 and [ADR 0014](adr/0014-backend-spi-boundary.md); the `llama_jni_entry.cpp -> #include "llama_jni.cpp"` pattern was removed through RA-0. `scripts/verify_architecture.py` now carries zero intended dependency or `.cpp`-include exceptions.

RA-0/RA-1 are stable baseline. RA-2 may continue internal decomposition; RA-3 must formalize mutable lifecycle semantics before RA-4/RA-5 behavior changes.

## Current parallel work

- **Architecture:** integrate RA-2 planning, then map the next extraction against RA-3 transitions without creating a second mutable runtime owner.
- **Quality:** define RA-10 fixture ownership only when the same conformance contract has fake and llama.cpp consumers.
- **Observability/identity:** make RA-7/RA-9 changes additive to existing stores/fingerprints.
- **Security:** handle dependency trust, workflow action trust and release provenance as separate RA-11 slices.
- **Memory:** continue residency/pressure/warm-idle work; converge through RA-3/5/7/8.

## Verified findings

### RA-2A — Runtime ownership

`RuntimeOrchestrator` still owns sessions, scheduler, loaded model/context, deferred unload, memory pressure and generation lifecycle. The first slice extracts only `GenerationPlanningPolicy` for effective configuration, output constraints, context sizing and reasoning control, with focused pure tests. Session/context/residency/cancellation/cleanup state stays centralized. The next extraction must preserve this until RA-3 formalizes lifecycle ownership.

### RA-5A — Scheduler

`SingleDecodeScheduler` already has one active decode, priority with FIFO sequencing, queued/running cancellation, `cancelAll()` and close. Its waiting structure is an unbounded `PriorityBlockingQueue`; RA-5 therefore needs explicit capacity and typed rejection, then fairness/starvation, cancellation-latency and slow-consumer/backpressure coverage. Simultaneous decode remains deferred.

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
