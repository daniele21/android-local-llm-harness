# Local AI reference architecture hardening progress

Status: active
Document type: workstream-state
Owner: repository
Canonical scope: state.reference-architecture-hardening
Read when: determining current reference-architecture hardening status, dependencies or the next implementation slice
Last reviewed: 2026-08-16

Canonical target: [`reference-architecture-hardening-plan.md`](reference-architecture-hardening-plan.md)

This tracker owns concise workstream state only. Repository-wide baseline/blockers/next work remain in [`current-state.md`](current-state.md); capability outcomes remain in [`roadmap.md`](roadmap.md). The plan was created from `dev@0a3ae6382e752d2eae49cc5379be778cb76ea2e1`; every implementation slice starts from the latest green `dev` available when it begins.

## Status legend

- `DONE`: target behavior, automated acceptance and required evidence/docs are integrated.
- `PARTIAL`: meaningful target work exists, but architecture, tests or evidence remain.
- `DEVICE`: implementation/automation are integrated; representative device evidence remains.
- `PENDING`: focused target work has not started.

## Milestone status

| ID | Status | Current boundary | Remaining gate | Depends on |
| --- | --- | --- | --- | --- |
| RA-0 Architecture fitness | PARTIAL | Canonical Gradle inventory and executable dependency/native fitness rules are integrated; final `.cpp` include cleanup is implemented with zero verifier exceptions on the active slice | Native/Android/package validation and integration of the zero-exception cleanup | None |
| RA-1 Backend SPI/fake | DONE | `core:backend-spi`, store-neutral `BackendModelSource`, llama.cpp adapter isolation and deterministic fake are integrated in `dev`; runtime-core has no concrete backend implementation dependency | Expand the contract into RA-10 conformance coverage as lifecycle/failure semantics mature | RA-0 fitness baseline |
| RA-2 Runtime kernel | PARTIAL | RA-2A mapped runtime state/mutation ownership and selected stateless generation planning as the first safe extraction seam | Extract/test planning policy while keeping all mutable lifecycle state in one runtime owner | RA-0, RA-1 |
| RA-3 Lifecycle/state machines | PARTIAL | load/generate/cancel/release/shutdown and pressure behavior exist | Code/test transition tables, ownership matrix, idempotent terminals, one residency path | RA-2 |
| RA-4 Failure/recovery | PARTIAL | Typed privacy-safe failures/recovery exist in several paths | Cross-layer recovery matrix + deterministic fault/race injection | RA-1, RA-3 |
| RA-5 Scheduling/backpressure | PARTIAL | Single decode, priority and cancellation exist | Queue/buffer bounds, admission/fairness, slow-consumer and cancellation-latency semantics | RA-3 |
| RA-6 Shared runtime | PARTIAL | Binder contract/client/service-host modules and substantial Consumer API lifecycle behavior are integrated | Verified version/capability/death/reconnect/orphan-cleanup gap audit, then behavior changes after RA-3/4/5 | RA-3/4/5 |
| RA-7 Observability | PARTIAL | Request-correlated telemetry already covers queue, prepare, TTFT/prefill/decode/total and effective generation metadata | Add only missing lifecycle/recovery correlation: session/execution identity, cleanup/unload/recovery/cancel latency and request-resource joins | RA-0; continuous |
| RA-8 Device policy | PARTIAL | Compatibility, profiles, memory/thermal capture and tuning work exist | Pure versioned planner using validated device/model/resource inputs | RA-7 + evidence |
| RA-9 Execution identity | PARTIAL | Artifact identity, benchmark execution fingerprints and rich evaluation semantic/runtime identities already exist | Reconcile the overlapping identity owners/versioning before introducing any shared runtime execution identity; no parallel identity store | RA-0 |
| RA-10 Backend testkit | PARTIAL | Deterministic SPI fake plus native/JNI/simulated acceptance are integrated | Reusable SPI conformance suite shared by fake and llama.cpp, then expand with RA-3/4/5 cases | RA-1 |
| RA-11 Security/provenance | PARTIAL | llama.cpp pin, Gradle wrapper checksum, secure download, signing/versioning, packaging and privacy rules exist | Gradle dependency verification, immutable CI action pinning, SBOM/provenance/release metadata after exact workflow/dependency audit | RA-0 |
| RA-12 Certification | PENDING | Existing evidence tooling covers several gates | Cumulative architecture/resilience/packaging/device evidence | Applicable RA-1..11 |

## Active architecture debt

The original two concrete architecture violations are almost closed:

- `runtime-core -> backends:llama-cpp` is removed and permanently guarded; RA-1 is integrated on `dev` through the backend SPI boundary recorded in [ADR 0014](adr/0014-backend-spi-boundary.md);
- the final native debt, `llama_jni_entry.cpp -> #include "llama_jni.cpp"`, has been removed in the active RA-0 native slice by compiling `llama_jni.cpp` directly and deleting the redundant entry source. It remains `PARTIAL` until native, Android and packaging validation pass and the exact head integrates into `dev`.

The architecture verifier now carries no intended dependency exception and no intended native `.cpp`-include exception on the cleanup slice. Any recurrence must fail CI instead of becoming implicit debt.

## Dependency routing

```text
RA-0 -> RA-1 -> RA-10
  |       \-> RA-4 <- RA-3
  +-> RA-2 -> RA-3 -> RA-5
  |                  \-> memory/residency integration
  +-> RA-7 -> RA-8
  +-> RA-9
  +-> RA-11
RA-3 + RA-4 + RA-5 -> RA-6
applicable RA-1..11 -> RA-12
```

RA-1 is now integrated. RA-2 can proceed on the stable SPI boundary while the isolated RA-0 native cleanup completes; RA-3 still waits for the runtime state-owner boundary to stabilize.

## Parallel lanes

| Lane | Current work | Integration constraint |
| --- | --- | --- |
| A Architecture core | Finish RA-0 native cleanup; start RA-2 stateless planning extraction | RA-3 waits for stable single state owner |
| B Quality/resilience | Seed RA-10 conformance on integrated SPI | RA-4/5 later consume RA-3 semantics |
| C Observability/device | RA-7 verified-gap design; RA-8 planner design can follow | Measured RA-8 waits for resource/device evidence |
| D Identity/security | RA-9 identity reconciliation + RA-11 supply-chain hardening | Reuse existing owners/stores and version fingerprints explicitly |
| E Shared runtime | RA-6 verified gap audit | Behavior changes wait for RA-3/4/5 |
| Existing memory | Residency/pressure/warm-idle continues | Converge through RA-3/5/7/8 |

## Current implementation block

1. **RA-0 native cleanup:** validate direct `llama_jni.cpp` compilation, removal of `llama_jni_entry.cpp`, zero verifier exceptions, native host behavior and Android packaging; integrate exact green head.
2. **RA-2 first extraction:** move stateless generation-planning decisions out of `RuntimeOrchestrator` without moving session/model/context ownership; add focused pure tests before changing further orchestration.
3. **RA-10 seed:** turn the integrated deterministic fake into reusable backend conformance tests for lifecycle, prompt planning, streaming, cancellation and stable failure mapping.
4. **RA-7/RA-9 contract reconciliation:** design the minimum additive correlation/execution identity changes against existing observability, benchmark and evaluation identities before persistence schema changes.
5. **RA-11 first supply-chain slice:** add dependency integrity/action immutability in small reviewable steps after exact primary-source pin verification.

## Verified tranche-1 discovery outcomes

### RA-2A — Runtime ownership and first extraction seam

`RuntimeOrchestrator` currently owns mutable runtime state, session descriptors, scheduler coordination, loaded model/context ownership, integrity cache integration, deferred unload state, memory-pressure behavior and generation execution. `executeGeneration` additionally mixes configuration resolution, validation, reasoning control, prompt planning, context sizing, backend execution, telemetry and terminal mapping.

The first extraction must therefore be **stateless generation planning**, not a new mutable `SessionManager`. Move pure decisions such as generation configuration resolution/validation, reasoning control, output-constraint validation and context-size selection behind a focused collaborator. Keep sessions, residency, contexts, scheduler mutations and cleanup in one authoritative runtime owner until later RA-2/RA-3 slices prove a safer split.

### RA-7A — Existing telemetry versus missing correlation

Existing telemetry already records request ID, application/use-case, model digest, queue/load/TTFT/prefill/decode/total timing, tokens/throughput, effective preset and sampling values, context/prompt size, chat-template identity, stop reason and planning/context-creation timings. Missing target coverage is narrower than initially assumed: session/execution identity correlation, explicit cleanup/unload/recovery phases, cancellation latency and a defined way to correlate resource snapshots with a request/run. Extend existing owners; do not create another telemetry store.

### RA-9A — Identity overlap to reconcile

The repository already has three strong identity layers: immutable model artifact digest, `BenchmarkExecutionIdentity`, and evaluation semantic/runtime/run fingerprints. Evaluation already carries model profile/quantization, backend revision, device/API/ABI, harness build identity, runtime tuning profile, load/warmup policy and semantic generation settings. RA-9 must first define ownership/versioning between these layers; silently changing existing fingerprint composition would invalidate comparisons. Any general runtime execution identity should be an additive/versioned contract reused by benchmark/evaluation rather than a fourth competing store.

### RA-11A — Supply-chain baseline

The Gradle wrapper distribution is SHA-256 pinned and the llama.cpp revision has an explicit repository guard. Remaining verified gaps include absence of Gradle dependency verification metadata, mutable version tags for GitHub Actions in workflows, and no current SBOM/provenance artifact found in the repository. Address these independently so dependency trust, workflow trust and release provenance remain reviewable and reversible.

### RA-6A — Shared-runtime audit boundary

The current build contains dedicated in-process transport, Binder contract/client and Android service-host modules plus the shared-runtime consumer fixture. Detailed version/capability/death/reconnect semantics still require a focused source/test audit before any protocol change; do not infer missing behavior from the module layout alone.

## Memory coordination

Current memory work continues: keep one residency owner; represent unload causes explicitly; let scheduler state become authoritative for idle decisions; use existing resource/telemetry owners; treat device PSS/thermal data as evidence rather than universal thresholds; migrate through RA-3/RA-5 contracts without unnecessary user-visible change.

## Update and completion rule

On status/blocker/dependency/next-slice change, update this tracker in the same change. Update `current-state.md` only for repository-wide operational state, `roadmap.md` only for capability outcomes, the target only for intent/acceptance changes, and `architecture.md`/ADR only for durable boundaries/decisions.

A milestone is `DONE` only after integration into `dev`, targeted acceptance, applicable regression guards, failure/cancel/cleanup coverage, required docs, cumulative green `dev`, and required physical evidence. Use `DEVICE` when implementation is integrated but representative hardware evidence remains. The workstream closes only after RA-12 removes/owns all P0 exceptions and demonstrates the reference-grade properties.
