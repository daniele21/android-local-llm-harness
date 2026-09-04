# Local AI reference architecture hardening target

Status: active
Document type: target-specification
Owner: repository
Canonical scope: target.reference-architecture-hardening
Read when: changing cross-cutting runtime, backend, lifecycle, failure, scheduling, observability, device-policy or reproducibility boundaries
Last reviewed: 2026-08-16

## Purpose

This document defines the architecture-hardening target for making Android Local LLM Harness a reusable reference for local-AI infrastructure rather than only a working llama.cpp application. Current status and the next slice belong in [`reference-architecture-hardening-progress.md`](reference-architecture-hardening-progress.md) and [`current-state.md`](current-state.md); capability sequencing belongs in [`roadmap.md`](roadmap.md); completion remains governed by [`definition-of-done.md`](definition-of-done.md).

Ordinary work starts from the latest green `dev` and targets `dev`. This target was introduced from planning snapshot `dev@0a3ae6382e752d2eae49cc5379be778cb76ea2e1`; implementation branches use the then-current green `dev`, not that historical commit.

## Scope and non-goals

The hardening target covers dependency direction, backend replaceability, runtime state ownership, lifecycle/resource state machines, failure/recovery, fault injection, bounded scheduling/backpressure, shared-runtime ownership semantics, privacy-safe observability, device-aware execution policy, reproducible identity, backend conformance testing, security/build provenance, architecture fitness rules and final evidence.

Memory/RAM-residency work remains an existing concern. This plan does not create another memory subsystem or duplicate warm-idle TTL, pressure handling, PSS or residency policy; it defines the lifecycle, scheduler, observability and device-policy seams through which that work integrates.

This effort does not itself add a second production backend, simultaneous decode, speculative decoding, multimodal inference, embeddings/rerankers, a replacement model store, a second telemetry/evaluation system or a product-UX redesign. A deterministic fake backend proves replaceability before any second real backend is considered. New modules are created only when they immediately own a real dependency/reuse/testing boundary.

## Reference-grade properties

The target is reference-grade only when it demonstrates:

1. deterministic lifecycle and one owner for every model/runtime/session/generation/transport resource;
2. executable dependency/native boundaries rather than documentation-only rules;
3. runtime-core compilation/testing without llama.cpp implementation dependencies;
4. explicit bounds for queueing, streaming buffers, contexts and retained diagnostics;
5. explicit concurrency, admission, cancellation and ownership semantics;
6. typed failures with bounded recovery consequences;
7. recovery after failure, cancellation, client death and process death;
8. reconstructable lifecycle/performance without persisting prompt or generated content;
9. reproducible artifact, runtime configuration and execution-policy identity;
10. evidence-driven hardware policy rather than desktop assumptions;
11. replaceable backend, transport, scheduler and stores where replacement is intended;
12. separate simulated, native, packaging and physical-device evidence levels.

## Target topology

```text
Apps / consumer surfaces
        |
Public runtime/client contracts
        |
Composition root
   |             |
Runtime core   Model plane
   |
Backend SPI
   |
llama.cpp adapter
   |
Thin JNI boundary
   |
llama.cpp

Cross-cutting: observability | evaluation/testkit | device policy |
               security/integrity | memory/resource governance
```

Shared runtime preserves the same ownership direction:

```text
client -> transport client -> Binder/AIDL -> runtime service -> runtime core -> backend SPI
```

Transport serializes commands/results/lifecycle signals but does not own model, scheduler, retry, generation or memory policy.

## Architectural invariants

- runtime lifecycle state has one authoritative owner;
- installation, selection and RAM residency stay distinct;
- artifact identity never derives from filename/display name;
- runtime core does not depend on UI or backend implementation internals;
- UI/transports do not duplicate generation, model, retry, queue or memory policy;
- native pointers/backend structures never cross public contracts;
- sync and streaming reuse the same underlying generation path;
- valid repeated cancel/close/release/unload operations are idempotent where required;
- raw backend exceptions do not become public/UI contracts;
- prompts/output/private paths remain outside normal telemetry;
- every slice adds lowest-useful-layer tests before dependent work advances;
- a milestone is not `DONE` while required evidence is missing.

## Workstream map

| ID | Priority | Outcome | Depends on |
| --- | --- | --- | --- |
| RA-0 | P0 | Executable architecture/native fitness rules | None |
| RA-1 | P0 | Backend SPI + deterministic fake; runtime independent from llama.cpp implementation | RA-0 |
| RA-2 | P0 | One runtime kernel/state owner with focused collaborators | RA-0; final backend seam uses RA-1 |
| RA-3 | P0 | Explicit lifecycle/resource state machines | RA-2 integration boundary |
| RA-4 | P0 | Typed failure/recovery matrix + fault injection | RA-1, RA-3 |
| RA-5 | P0/P1 | Bounded scheduling/backpressure/cancellation semantics | RA-3 |
| RA-6 | P1 | Ownership-safe shared-runtime protocol behavior | RA-3, RA-4, RA-5 |
| RA-7 | P1 | End-to-end privacy-safe correlation and lifecycle observability | RA-0; expands continuously |
| RA-8 | P1 | Pure evidence-driven device execution policy | RA-7 + device/memory evidence |
| RA-9 | P1 | Reproducible artifact/execution identity | RA-0 |
| RA-10 | P1 | Shared backend conformance/resilience suite | RA-1; expands with RA-3/4/5 |
| RA-11 | P1/P2 | Verifiable local-first and build/supply provenance | RA-0 |
| RA-12 | P1 | Cumulative reference-grade certification | Applicable RA-1..RA-11 |

## RA-0 — Architecture baseline and fitness rules

**Problem:** architecture intent already exists, but violations must fail automatically. At the planning baseline, `core:runtime-core` directly depends on `:backends:llama-cpp`, while `llama_jni_entry.cpp` includes `llama_jni.cpp` despite the native Definition of Done.

**Deliverables:** record approved module direction; add deterministic forbidden-edge checks; reject implementation `.cpp` includes; compile/link native units normally; prevent apps/UI from reaching JNI internals and transport from owning runtime policy; derive CI module scope from canonical Gradle/settings metadata where possible; explicitly own any temporary exception.

**Exit gate:** injected forbidden dependency and `.cpp` include fixtures fail locally/CI; baseline violations are removed by their owning milestone or explicitly tracked; documentation/navigation point to the executable rules.

## RA-1 — Backend SPI and deterministic fake

**Problem:** runtime policy has a concrete llama.cpp build dependency, weakening isolation and failure simulation.

**Deliverables:** define the smallest backend contract without JNI/native types; use an existing coherent contract owner or create `:core:backend-spi` only when it immediately owns production behavior/tests; keep llama.cpp adaptation in `:backends:llama-cpp`; inject it at composition roots; add deterministic load/stream/cancel/release/failure fake behavior; remove `:backends:llama-cpp` from runtime-core.

**Exit gate:** runtime-core compiles/tests without llama.cpp implementation; fake drives a complete simulated lifecycle; real application composition is consumer-compatible; RA-0 enforces the direction.

## RA-2 — Runtime kernel decomposition

**Problem:** orchestration currently combines lifecycle, scheduling, planning, context/backend work, telemetry, failures and cleanup; splitting state among helpers would make this worse.

**Deliverables:** identify one `RuntimeKernel`-equivalent state authority; keep stable public entry points through a facade; incrementally extract real responsibilities such as session management, generation coordination, context preparation, backend gateway and telemetry emission; keep collaborators stateless where possible; preserve cleanup order and model/context ownership; remove complexity suppressions only as ownership genuinely shrinks.

**Exit gate:** one documented runtime-state source of truth; no collaborator independently mutates the same lifecycle state; current generation/cancellation/model-switch/memory-pressure/shutdown behavior remains green; extracted logic has isolated tests.

## RA-3 — Lifecycle and resource state machines

**Problem:** memory, cancellation and recovery safety require legal transitions and ownership to be explicit rather than implicit call order.

**Deliverables:** separate artifact/install, runtime residency, session and generation state machines; define legal transitions/stable invalid-transition failures; own model handles, contexts, sessions, active/queued generations and client registrations explicitly; define deterministic cleanup for complete/fail/cancel/client-death/shutdown; route memory pressure, explicit unload and warm-idle eviction through the same residency semantics with typed reasons.

**Exit gate:** transition tables live in code/tests; illegal transitions cannot leak resources; repeated valid terminal operations are safe; teardown during queued/active work is defined; memory work uses the same residency owner.

## RA-4 — Failure, recovery and fault injection

**Problem:** reference behavior must specify the consequence of failure, not only map exceptions after the fact.

**Deliverables:** typed families for integrity/storage, compatibility, load/init, context, generation, cancellation, resource pressure, transport and invariant failures; recovery matrix selecting bounded retry, request reset, session close, unload, runtime restart, user action or terminal failure; no raw backend error leakage; deterministic fake controls for load timeout/failure, token-N failure, slow decode, cancellation races, malformed/corrupt result paths where representable, resource exhaustion and process/service death fixtures.

**Exit gate:** every public failure has a stable category/recovery consequence; injected failures leave no unowned resources; recoverable request failures do not poison runtime unless policy requires reset; failure/recovery telemetry is privacy-safe.

## RA-5 — Scheduling, backpressure and cancellation

**Problem:** one active decode is a sound default, but capacity, admission, buffering and cancellation must be explicit policy.

**Deliverables:** retain max active decode `1`; define bounded waiting queue, deterministic admission/rejection, priority/fairness/starvation expectations, queued vs active cancellation, cancellation propagation/latency measurement, bounded buffers and slow/dead consumer behavior across JNI/Kotlin/Binder; expose wait/rejection/cancellation/drain metrics; let RAM warm-idle policy consume authoritative scheduler idle state.

**Exit gate:** queue/buffers cannot grow unbounded; slow/dead consumers have deterministic consequences; queued/active cancellation and shutdown races are tested; simultaneous decode remains deferred.

## RA-6 — Shared-runtime protocol hardening

**Problem:** substantial shared-runtime/Binder work already exists; hardening must close verified ownership/death/compatibility gaps without duplicating runtime policy.

**Deliverables:** audit existing version/capability semantics first; verify request/session/client identity, idempotency expectations, deadline/cancellation propagation, client death, service death, reconnect and orphan cleanup; release dead-client resources through runtime ownership; preserve signer/authorization and existing Consumer API compatibility; keep DTO/AIDL evolution backward-compatible.

**Exit gate:** death/reconnect tests leave no orphan resources; incompatibility fails closed; Binder/in-process paths converge on runtime semantics; transport owns no model/scheduler/retry/memory policy; physical signer/process evidence remains a distinct release gate.

## RA-7 — End-to-end observability contract

**Problem:** rich telemetry already exists; the gap is standardized correlation/lifecycle coverage, not a second telemetry system.

**Deliverables:** one correlation context spanning queue, context preparation, prefill, first token, decode, cancellation and cleanup; correlate session/model execution/transport identities where available; reuse existing stores/privacy rules; standardize load/unload reason, queue wait, cancellation latency, context size, active/queued work, backend error category and recovery outcome; integrate PSS/memory/thermal through existing resource observability with explicit bounds/units/availability.

**Exit gate:** one inference/failure can be reconstructed chronologically without prompt/output/private data; cold/warm, queue, generation, cancellation and cleanup phases are distinguishable; diagnostics remain presentation owner.

## RA-8 — Device-aware execution policy

**Problem:** mobile defaults must derive from evidence without introducing unstable self-tuning.

**Deliverables:** pure planner consuming device capabilities, model profile, current resource state and workload; explicit backend execution plan using only currently supported knobs; separate policy from mechanism; conservative versioned defaults first; use reliable measured memory/thermal inputs; distinguish peak vs sustained performance; record effective policy/version/plan; exclude unsupported GPU/speculative/multimodal choices.

**Exit gate:** identical inputs produce identical plans; policy tests need neither Android nor llama.cpp; effective choices are reproducible; pressure/thermal states cannot silently select unvalidated configs; measured policies require representative-device evidence.

## RA-9 — Artifact and execution identity hardening

**Problem:** SHA-256 content identity already solves artifact identity; reproducible evaluation additionally needs exact execution identity without changing artifact semantics.

**Deliverables:** preserve artifact digest as canonical artifact identity; define separate execution identity composed only from material inputs such as artifact digest, quantization/metadata, tokenizer/template identity where applicable, backend/runtime version, profile/preset and execution-policy version; audit interrupted/duplicate/verification/atomic install invariants rather than replace ModelStore; persist only minimum identity required by benchmark/evaluation/evidence owners; fail closed on evidence identity mismatch.

**Exit gate:** materially different configurations cannot accidentally share reproducibility identity; presentation metadata does not affect artifact identity; failed install never publishes ambiguous state; evidence points to exact identities without model bytes/sensitive content.

## RA-10 — Backend contract and resilience testkit

**Problem:** backend replaceability is real only if implementations satisfy the same lifecycle/failure contract.

**Deliverables:** reusable SPI conformance suite for fake and llama.cpp adapter where appropriate; cover load/release/repeat lifecycle, session/context, stream/Unicode/stop/cancel, malformed input, context boundaries, close during generation and error mapping; extend with RA-4 fault and RA-5 scheduler cases; retain pure C++ native tests and Kotlin JNI mapping tests; keep simulation distinct from real-GGUF certification.

**Exit gate:** backend compatibility requires the common suite; fake/real adapters expose the same public lifecycle semantics; regression fixtures are deterministic/offline; physical evidence is never inferred from fake success.

## RA-11 — Security and supply-chain reproducibility

**Problem:** a local-first reference must make privacy and provenance verifiable.

**Deliverables:** preserve local data plane for prompt/output/inference; keep optional network catalog/download control-plane explicit; retain digest/provenance/compatibility checks; audit Gradle dependency verification, native/toolchain pins and CI action integrity before adding mechanisms; add/strengthen SBOM/provenance where useful; record app/runtime/backend/NDK-relevant versions in evidence; preserve signer/least-authority boundaries; test path/URI/error/log leakage when boundaries change.

**Exit gate:** local inference gains no undeclared network dependency; model/app artifacts trace to verified identities; release evidence records reproducibility versions; diagnostics remain privacy-safe under failures; security-sensitive dependency changes are review-visible.

## RA-12 — Reference-grade certification

**Deliverables:** run full simulated lifecycle on fake + production runtime core; backend conformance/native tests; architecture/format/static-analysis/lint/build/packaging gates; repeated physical real-GGUF lifecycle, cancellation, memory stability, latency/throughput and thermal evidence; shared-runtime death/reconnect evidence when included in the claim; reconcile architecture/ADR/API/roadmap/current-state/DoD; publish only the achieved evidence level.

**Exit gate:** all P0 boundaries integrated, no unowned P0 exception, cumulative `dev` green, and representative physical evidence present for every production-readiness/performance claim.

## Dependency and parallelization model

```text
RA-0 -> RA-1 -> RA-10 -------------------------|
  |       |                                     |
  |       +-> RA-4 ----------------------|      |
  +-> RA-2 -> RA-3 -> RA-5 -------------+-> RA-6
  |               \-> memory/residency integration
  +-> RA-7 -> RA-8
  +-> RA-9
  +-> RA-11
applicable RA-1..RA-11 -----------------------> RA-12
```

Parallel lanes:

- **A — architecture core:** RA-0 -> RA-1; RA-2 inventory/extraction may begin after RA-0 on disjoint internals, then converges with RA-1; RA-3 waits for the stable state-owner boundary.
- **B — quality/resilience:** RA-10 starts with RA-1; after RA-3, RA-4 and RA-5 can integrate in parallel and expand RA-10.
- **C — observability/device:** RA-7 starts after RA-0 and expands continuously; RA-8 design may proceed, but measured integration waits for reliable resource/device evidence.
- **D — identity/security:** RA-9 and RA-11 can start after RA-0 and must reuse current ModelStore/evaluation/security owners.
- **E — shared runtime:** RA-6 audit can start early; behavior changes wait for RA-3/4/5 so transport does not freeze unstable lifecycle semantics.
- **Existing memory lane:** continues independently; RA-3 owns residency transitions, RA-5 authoritative idle state, RA-7 resource correlation, RA-8 policy inputs and RA-12 repeated device stability.

## Incremental delivery

1. **Tranche 1 — enforce seams:** RA-0 + RA-1, while RA-7/RA-9/RA-11 audits and RA-2/RA-6 verified-gap discovery run in parallel.
2. **Tranche 2 — stabilize kernel:** RA-2 + RA-3 + initial RA-10. Exit only with one runtime state owner and explicit transitions.
3. **Tranche 3 — deterministic stress:** RA-4 and RA-5 in parallel; expand RA-7/RA-10. Exit with bounded saturation/cancellation/failure outcomes.
4. **Tranche 4 — platform boundaries:** RA-6 + RA-8 and remaining RA-9/RA-11 gaps. Exit with consistent process/device/artifact semantics.
5. **Tranche 5 — certify:** RA-12 cumulative automated and physical evidence plus documentation reconciliation.

## Branch, documentation and validation discipline

- each slice starts from latest green `dev`; use small focused branches/PRs rather than one long-lived hardening branch;
- parallel branches should own disjoint files/contracts; consumers wait for producer contracts to integrate, then refresh from `dev`;
- behavior-changing slices add targeted tests before dependent slices and do not become complete until cumulative `dev` stays green;
- update the progress tracker with every state/blocker/dependency/next-slice change; update `current-state.md` only for repository-wide state, `roadmap.md` only for capability outcomes, this target only when intent/acceptance changes, and `architecture.md`/ADR when durable boundaries change;
- use an ADR for expensive-to-reverse choices such as SPI ownership, runtime state ownership, public lifecycle states, exposed queue/backpressure semantics, Binder compatibility, execution-policy versioning or reproducibility identity;
- every slice follows the Definition of Done: lowest-useful-layer tests, failure/cancel/cleanup coverage, formatter/static analysis/compilation, architecture/docs checks, native/JNI/packaging gates where applicable, PR + cumulative `dev` validation, and representative physical evidence before production/memory/performance claims.
