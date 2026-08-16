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
| RA-0 Architecture fitness | DONE | Module/dependency/native guards integrated with zero verifier exceptions | Keep guards green |
| RA-1 Backend SPI/fake | DONE | Backend-neutral SPI, store-neutral source, llama.cpp isolation and deterministic fake integrated | Expand through RA-10 |
| RA-2 Runtime kernel | PARTIAL | Stateless generation planning extracted; lifecycle ownership explicit while orchestration/context ownership remains centralized | Continue only state-preserving extractions |
| RA-3 Lifecycle/state machines | DONE | Session, generation and physical model-residency transitions have explicit non-overlapping owners and integration coverage | RA-4/RA-5 consume these boundaries |
| RA-4 Failure/recovery | PARTIAL | Typed failures/recovery exist in several paths | Recovery matrix + deterministic fault/race injection |
| RA-5 Scheduling/backpressure | PARTIAL | Single decode, priority/FIFO, bounded queue/rejection, cancellation and close-race handling integrated | Fairness, cancellation latency, slow/dead-consumer semantics |
| RA-6 Shared runtime | PARTIAL | Negotiation, epochs, reconnect and client-death cleanup exist | Idempotency/deadlines if required + process-death evidence after RA-4/5 |
| RA-7 Observability | PARTIAL | Rich request-correlated telemetry exists | Session/execution identity, cleanup/recovery/cancel latency joins |
| RA-8 Device policy | PARTIAL | Compatibility plus memory/thermal evidence exists | Pure versioned planner after reliable RA-7/device evidence |
| RA-9 Execution identity | PARTIAL | Artifact, benchmark and evaluation identities exist | Reconcile/version ownership; no parallel identity store |
| RA-10 Backend testkit | PARTIAL | Deterministic fake plus native/JNI/simulated tests exist | Shared conformance fixture with fake + llama.cpp consumers |
| RA-11 Security/provenance | PARTIAL | llama.cpp/wrapper pins, secure download, signing and privacy controls exist | Dependency verification, immutable action pins, SBOM/provenance |
| RA-12 Certification | PENDING | Evidence infrastructure exists | Cumulative automated + representative-device certification |

## Baseline and current work

RA-0/RA-1 baseline debt is closed: the runtime-to-concrete-backend dependency and `.cpp` implementation include were removed, and the architecture verifier has zero intended exceptions.

RA-3 is closed. `SessionLifecycle` owns request admission, close intent, active-request drain and release reservation. `GenerationLifecycle` owns accepted-request cancellation intent and terminal-once delivery. `ModelResidencyLifecycle` owns the physical resident model handle and load/unload state. `SingleDecodeScheduler` remains the sole owner of queued/running decode state. Session context remains a single session-owned handle serialized through `resourceLock`; failure/race injection for context create/release belongs to RA-4, not another state machine.

Current lanes:
- **RA-2:** continue state-preserving decomposition only where the closed lifecycle boundaries remain intact.
- **RA-4:** define internal failure families/recovery consequences, then add deterministic injection across load/context/generation/cancel/release/unload.
- **RA-5:** finish fairness/starvation, cancellation latency and bounded slow/dead-consumer behavior.
- **RA-7/9:** extend existing telemetry/identity stores rather than creating parallel owners.
- **RA-10/11:** add real cross-backend conformance and supply-chain mechanisms only where evidence justifies them.
- **Memory:** software admission, warm-idle, cost-profile and observation foundations are integrated; device calibration remains a hardware gate.

## Verified findings

### RA-2 — Runtime ownership

`GenerationPlanningPolicy` owns stateless configuration/context-sizing/reasoning planning. `RuntimeOrchestrator` keeps the session registry, session context handles, backend-operation serialization, memory-pressure coordination and generation orchestration. Lifecycle-specific state is delegated to the explicit RA-3 owners.

### RA-3 — Completion evidence

Session lifecycle replaces the previous `activeRequests`, `closing` and `released` atomics and covers close-during-generation, post-close rejection, exactly-once release and release-failure retry. Generation lifecycle replaces separate cancellation/terminal atomics with `OPEN -> CANCELLING -> TERMINAL`; queued/running remains scheduler-owned.

Model residency uses `NOT_RESIDENT -> LOADING -> RESIDENT -> UNLOADING -> NOT_RESIDENT` and owns the single resident handle. Load failure returns to `NOT_RESIDENT`; unload failure restores `RESIDENT` with the same handle. Model replacement preserves: artifact verification -> old-model unload -> model-load memory admission -> backend initialization -> load reservation -> native load -> publication. Tests cover load/unload rollback and retry, switch ordering, physical-handle visibility during unload and unload-before-shutdown idempotency.

`ModelStore` still owns installation/integrity; memory planners own admission; warm-idle/pressure policy decide when to evict; the scheduler owns decode activity. The context exit audit found one context-handle owner with serialized mutation and no justified parallel state machine.

The exit gate is satisfied by transition/unit tests, integration teardown/rollback coverage and cumulative green `dev@30a0f60dcbe3fe699013b5bde35acb90b2a25356` across repository guards, Android validation and native host tests.

### RA-5 — Scheduler

`SingleDecodeScheduler` enforces one active decode, priority/FIFO sequencing, bounded waiting admission with deterministic rejection, queued/running cancellation, `cancelAll()` and close. Remaining work is fairness/starvation policy, cancellation-latency evidence and bounded streaming/backpressure behavior. Simultaneous decode remains deferred.

### RA-6 — Shared runtime

Binder already provides version/feature negotiation, typed incompatibility, connection epochs/loss, reconnect and client-death cleanup. Remaining work is operation replay/idempotency only if needed, deadlines where justified, and physical service-process death/reconnect evidence after RA-4/5.

### RA-7 / RA-9 — Telemetry and identity

Telemetry already covers request/app/use-case/model identity and major generation timings/settings. Remaining correlation is mainly session/execution identity, cleanup/unload/recovery, cancellation latency and request-resource joins. Artifact, benchmark and evaluation identities already exist; RA-9 must reconcile/reuse them rather than add a fourth store.

### RA-10 / RA-11 — Testkit and supply chain

The deterministic fake supports controlled failures, blocking, streaming, cancellation and release accounting; create shared conformance fixtures only when both fake and llama.cpp consume them. Gradle wrapper and llama.cpp revision are pinned; remaining supply-chain gaps include dependency-verification metadata, immutable action references and SBOM/provenance.

## Update rule

Update this tracker with state/blocker/next-slice changes; update `current-state.md` only for repository-wide state, `roadmap.md` only for capability outcomes, and architecture/ADRs only for durable boundaries. A milestone becomes `DONE` only after integration, targeted regression coverage, required docs, cumulative green `dev` and any required device evidence.
