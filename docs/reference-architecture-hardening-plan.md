# Local AI reference architecture hardening target

Status: active
Document type: target-specification
Owner: repository
Canonical scope: target.reference-architecture-hardening
Read when: changing cross-cutting runtime, backend, lifecycle, failure, scheduling, observability, device-policy or reproducibility boundaries
Last reviewed: 2026-08-16

## Purpose

This document defines the architecture-hardening target for making Android Local LLM Harness a reusable reference for local-AI software infrastructure rather than only a working llama.cpp application.

Current implementation state and the next focused hardening slice belong in [`reference-architecture-hardening-progress.md`](reference-architecture-hardening-progress.md) and [`current-state.md`](current-state.md). Capability-level sequencing remains in [`roadmap.md`](roadmap.md). Merge and production completion remain governed by [`definition-of-done.md`](definition-of-done.md).

Ordinary implementation work starts from the latest green `dev` and targets `dev`. The planning snapshot that introduced this target was based on `dev` commit `0a3ae6382e752d2eae49cc5379be778cb76ea2e1`; implementation branches must use the then-current green `dev`, not this historical snapshot.

## Scope

The hardening target covers:

- dependency direction and backend replaceability;
- runtime state ownership and orchestration decomposition;
- explicit lifecycle and resource state machines;
- failure taxonomy, recovery policy and fault injection;
- bounded scheduling, cancellation and backpressure;
- existing shared-runtime protocol ownership and process-death behavior;
- request-correlated, privacy-safe observability;
- device-aware execution policy;
- reproducible model and execution identity;
- backend contract and resilience testing;
- security, build and supply-chain reproducibility;
- architecture fitness rules and final reference-grade evidence.

Memory and RAM-residency work is already an active repository concern. This plan does not create a competing memory subsystem or duplicate warm-idle TTL, memory-pressure, PSS or residency policy. It defines the lifecycle, scheduling, observability and device-policy boundaries through which that work must integrate.

## Non-goals

This hardening effort does not, by itself:

- add a second production inference backend;
- enable simultaneous decodes;
- introduce speculative decoding, multimodal inference, embeddings or rerankers;
- replace the existing content-addressed model store;
- replace existing telemetry, health, benchmark or model-evaluation systems;
- redesign the connected product UX;
- relax physical-device evidence gates;
- create empty modules merely to match a diagram.

A fake backend is intentionally preferred over a second real backend until backend independence is proven by tests.

## Reference-grade properties

The target architecture is considered reference-grade only when all of the following are true:

1. **Deterministic lifecycle** — every model, runtime, session, generation and transport resource has one owner, legal transitions and idempotent release behavior.
2. **Enforced boundaries** — dependency rules are executable CI constraints rather than documentation-only conventions.
3. **Backend independence** — runtime policy compiles and tests without llama.cpp in its dependency graph.
4. **Bounded resources** — queueing, streaming buffers, context growth and retained diagnostics have explicit bounds.
5. **Bounded concurrency** — active decode count, admission, cancellation and ownership rules are explicit.
6. **Typed failure semantics** — failures map to stable categories with defined recovery consequences.
7. **Recovery** — failure, cancellation, client death and process death leave the system in a known recoverable state.
8. **Observability** — lifecycle and performance can be reconstructed without persisting prompt or generated content.
9. **Reproducibility** — a measured run can identify the exact artifact, backend/runtime configuration and execution policy used.
10. **Hardware awareness** — execution policy consumes measured device constraints instead of embedding desktop assumptions.
11. **Replaceable infrastructure** — backend, transport, scheduler and stores remain behind explicit contracts where replacement is intended.
12. **Evidence-backed completion** — simulated, native, Android packaging and representative physical-device evidence remain distinct and auditable.

## Target dependency topology

```text
Apps / Consumer surfaces
          |
          v
Public runtime API / client contracts
          |
          v
Composition root
    |             |
    v             v
Runtime core    Model plane
    |
    v
Backend SPI
    |
    v
llama.cpp adapter
    |
    v
Thin JNI boundary
    |
    v
llama.cpp

Cross-cutting inputs/outputs:
Observability | Evaluation/TestKit | Device policy | Security/Integrity | Memory/Resource governance
```

For shared-runtime use, transport remains outside runtime state ownership:

```text
Client process -> transport client -> Binder/AIDL protocol -> runtime service -> runtime core -> backend SPI
```

The transport may serialize commands, results and lifecycle signals. It must not become an independent owner of runtime policy or model state.

## Architectural invariants

The following invariants apply across every milestone:

- runtime state has one authoritative owner;
- model installation, selection and RAM residency remain distinct concepts;
- artifact identity is never inferred from a display name or filename;
- runtime core does not depend on Android UI or backend implementation internals;
- UI and transports do not reimplement generation, model, retry, queue or memory policy;
- native pointers and backend structures never cross public contracts;
- synchronous and streaming generation share the same underlying generation behavior;
- cancellation, close, release and unload paths are deterministic and idempotent where repeat calls are valid;
- raw backend exceptions do not become public or UI error contracts;
- prompt and generated output remain outside normal telemetry and durable diagnostics;
- architecture changes are incremental and covered at the lowest useful layer before integration;
- a milestone is not `DONE` while required automated or physical evidence is missing.

## Workstream map

| ID | Priority | Workstream | Primary outcome | Depends on |
| --- | --- | --- | --- | --- |
| RA-0 | P0 | Architecture baseline and fitness rules | Executable dependency/native/documentation guardrails | None |
| RA-1 | P0 | Backend SPI and deterministic fake | Runtime no longer depends on llama.cpp implementation | RA-0 |
| RA-2 | P0 | Runtime kernel decomposition | One state owner with focused collaborators | RA-0; final backend seam uses RA-1 |
| RA-3 | P0 | Lifecycle and resource state machines | Legal transitions and ownership are explicit | RA-2 integration boundary |
| RA-4 | P0 | Failure, recovery and fault injection | Typed failure matrix and deterministic recovery tests | RA-1, RA-3 |
| RA-5 | P0/P1 | Scheduling, backpressure and cancellation | Explicit bounded concurrency semantics | RA-3 |
| RA-6 | P1 | Shared-runtime protocol hardening | Ownership-safe client/service death and compatibility behavior | RA-3, RA-4, RA-5 |
| RA-7 | P1 | End-to-end observability contract | Correlated lifecycle/performance evidence | RA-0; expands with all milestones |
| RA-8 | P1 | Device-aware execution policy | Pure, evidence-driven runtime planning | RA-7 plus device/memory evidence |
| RA-9 | P1 | Artifact and execution identity hardening | Reproducible identity without rebuilding the model store | RA-0 |
| RA-10 | P1 | Backend contract and resilience testkit | One conformance suite for fake and llama.cpp adapters | RA-1; expands with RA-3/4/5 |
| RA-11 | P1/P2 | Security and supply-chain reproducibility | Verifiable local-first and build provenance boundaries | RA-0 |
| RA-12 | P1 | Reference-grade certification | Cross-cutting automated and physical evidence | RA-1 through RA-11 as applicable |

## RA-0 — Architecture baseline and fitness rules

### Problem

Repository documentation already defines modularity and native-code rules, but important architecture constraints must fail automatically when violated. At the planning baseline, `core:runtime-core` directly declares `implementation(project(":backends:llama-cpp"))`, and `llama_jni_entry.cpp` includes `llama_jni.cpp` as an implementation file. Both are signals that architecture intent needs executable enforcement.

### Required changes

- record the current module dependency graph and approved dependency direction;
- add architecture checks for forbidden Gradle edges;
- prevent runtime-core from taking implementation dependencies on `backends/*` after RA-1 completes;
- prevent apps/UI modules from reaching native/JNI implementation packages directly;
- prevent transport modules from becoming owners of runtime state or model policy;
- add a native-source guard that rejects implementation `.cpp` inclusion from another `.cpp`;
- make CMake compile/link implementation units normally;
- remove duplicated CI module inventories where Gradle/settings can be the source of truth;
- make architecture checks deterministic, fast and runnable locally;
- document temporary exceptions explicitly and give each an owner/removal milestone.

### Acceptance

- a deliberately introduced forbidden dependency fails the architecture gate;
- a deliberately introduced `#include "*.cpp"` fails the native architecture gate;
- the normal repository validation invokes the guard without a manually maintained second module list;
- existing violations are either removed in the owning milestone or represented as explicit temporary debt, never silently grandfathered;
- architecture documentation and agent navigation point to the executable rules.

### Evidence

- focused architecture-test output;
- clean documentation/navigation verification;
- cumulative `dev` validation after integration.

## RA-1 — Backend SPI and deterministic fake

### Problem

Runtime policy currently has a concrete build-time dependency on the llama.cpp backend. That makes replacement, failure simulation and isolated runtime testing harder than the intended architecture allows.

### Required changes

- define the smallest stable backend contract required by runtime policy;
- prefer an existing appropriate contract owner when responsibilities remain coherent; otherwise create `:core:backend-spi` only when it immediately owns used production contracts and tests;
- move llama.cpp-specific backend adaptation behind `:backends:llama-cpp`;
- keep JNI/native types entirely inside the backend boundary;
- inject the concrete backend from composition roots;
- add a deterministic fake backend/test fixture supporting controlled load, stream, cancel, release and failure behavior;
- remove the concrete `:backends:llama-cpp` dependency from `core:runtime-core`;
- preserve existing public runtime behavior while the dependency direction changes.

### Acceptance

- `core:runtime-core` compiles and its core tests run with no llama.cpp implementation dependency;
- application composition with the real llama.cpp adapter remains unchanged from a consumer perspective;
- the fake backend can exercise a complete simulated lifecycle;
- native handles/types are not visible from SPI contracts;
- architecture fitness tests enforce the new direction.

## RA-2 — Runtime kernel decomposition

### Problem

Runtime orchestration has accumulated session lifecycle, scheduling, generation planning, context preparation, backend calls, telemetry, error mapping and cleanup responsibilities. Splitting it carelessly would merely distribute mutable state; the goal is one authoritative runtime state owner with focused collaborators.

### Required changes

- identify the exact state that must remain owned by a `RuntimeKernel` or equivalent single authority;
- keep public runtime entry points stable through a facade while internals move incrementally;
- extract only real responsibilities, such as session management, generation coordination, context preparation, backend gateway and telemetry emission;
- make collaborators stateless where possible and prevent them from independently owning runtime lifecycle state;
- keep scheduling policy replaceable but centrally coordinated;
- remove mixed-responsibility complexity suppressions as the owning logic moves, rather than hiding complexity in helper classes;
- preserve deterministic cleanup ordering and current model/context ownership.

### Acceptance

- one component is the documented source of truth for runtime lifecycle state;
- no extracted collaborator can independently mutate the same lifecycle state;
- facade/API behavior remains compatible;
- existing generation, cancellation, model-switch, memory-pressure and shutdown tests remain green;
- new unit tests cover extracted responsibilities without requiring Android or llama.cpp where not necessary.

## RA-3 — Lifecycle and resource state machines

### Problem

Memory safety, cancellation safety and recovery depend on lifecycle semantics being explicit rather than implied by call ordering.

### Required changes

- define separate state machines for artifact/install state, runtime model residency, session and generation;
- do not merge installation/selection/residency into one model status;
- define legal transitions and stable transition failures;
- define ownership for model handles, contexts, sessions, active generations, queued requests and transport-owned client registrations;
- make valid repeated close/cancel/release/unload operations idempotent;
- define cleanup order for complete, failed, cancelled, client-dead and shutdown paths;
- route memory pressure, explicit unload and future warm-idle TTL through the same residency transition model with typed unload reasons;
- make impossible/illegal transitions observable and testable.

### Acceptance

- transition tables are represented in code/tests rather than prose only;
- illegal transitions fail deterministically without leaking resources;
- repeated valid release operations do not double-free or corrupt state;
- session/model teardown during active or queued work has a defined result;
- memory-management work uses the same ownership and unload semantics instead of a parallel path.

## RA-4 — Failure, recovery and fault injection

### Problem

A reference architecture must define what the system does after each class of failure, not only map errors after they happen.

### Required changes

- establish typed failure families for storage/integrity, compatibility, load/init, context, generation, cancellation, resource pressure, transport and internal invariant failures;
- define a recovery matrix for each failure family: retry request, reset generation, close session, unload model, restart runtime, require user action or fail permanently;
- keep retry policy bounded and explicit; no implicit unbounded retries;
- prevent raw native/backend exception text from crossing public or UI boundaries;
- extend the fake backend into deterministic fault injection with controls for load timeout/failure, fail-on-token-N, slow decode, cancellation races, corrupted callbacks/stream results where representable, backend disconnect, simulated resource exhaustion and service death fixtures;
- verify runtime recovery after every recoverable injected failure.

### Acceptance

- every public failure maps to one stable typed category and recovery consequence;
- injected failures cannot leave an unowned model/context/session/generation resource;
- a failed request does not poison a reusable runtime unless policy explicitly requires reset;
- fault-injection tests reproduce race-prone paths deterministically;
- telemetry records category, phase and recovery outcome without sensitive payloads.

## RA-5 — Scheduling, backpressure and cancellation

### Problem

The current single-decode strategy is intentionally conservative, but queue capacity, admission, stream backpressure and cancellation deadlines must be explicit architecture policy rather than incidental implementation behavior.

### Required changes

- retain one active decode as the default reference policy;
- define a bounded waiting queue and a deterministic admission/rejection policy;
- define priority/fairness behavior and starvation expectations;
- specify queued cancellation separately from active cancellation;
- define cancellation propagation and a measurable cancellation-latency objective;
- define bounded streaming buffers and slow-consumer behavior across native callback, Kotlin flow/callback and Binder transport;
- define client-disconnect behavior for queued and active requests;
- expose queue wait, rejection, cancellation and drain behavior through observability;
- integrate RAM-residency/warm-idle decisions only after scheduler idle state is authoritative.

### Acceptance

- queue growth is bounded under load;
- a slow or dead consumer cannot cause unbounded buffering;
- cancellation has deterministic queued and active semantics;
- scheduler tests cover priority, fairness, saturation, cancellation races and shutdown;
- simultaneous decode remains disabled unless a future separately approved capability changes the policy.

## RA-6 — Shared-runtime protocol hardening

### Problem

The repository already has substantial shared-runtime and Consumer API/Binder work. The remaining architecture goal is to make process boundaries behave like a versioned, ownership-safe protocol without duplicating runtime policy in transport code.

### Required changes

- audit existing protocol version/capability semantics before adding new wire fields;
- ensure request/session/client identities are sufficient for correlation and cleanup without exposing native ownership;
- define idempotency expectations for reconnect-safe operations;
- propagate deadlines/cancellation across the process boundary where the existing contract supports it;
- define client death, service death, reconnect and orphan cleanup behavior;
- ensure resources owned on behalf of a dead client are released by runtime policy rather than ad hoc Binder callbacks;
- preserve same-signer/authorization boundaries and compatibility behavior;
- keep transport DTO/AIDL evolution backward-compatible according to the existing Consumer API policy.

### Acceptance

- process death and reconnect tests leave no orphan runtime resources;
- protocol incompatibility fails closed with a stable compatibility result;
- Binder and in-process paths converge on the same runtime semantics;
- transport modules do not own model selection, scheduling, retry or memory policy;
- physical same-signer and invalid-signer evidence remains a separate release gate.

## RA-7 — End-to-end observability contract

### Problem

The repository already records rich telemetry. Hardening should standardize correlation and lifecycle coverage rather than create a second telemetry system.

### Required changes

- define one request correlation context spanning queue, context preparation, prefill, first token, decode, cancellation and cleanup;
- correlate session, model artifact/execution identity and transport request where available;
- reuse existing telemetry stores and privacy rules;
- standardize phase timing and terminal outcome semantics;
- expose model load/unload reason, queue wait, cancellation latency, context size, active/queued work, backend error category and runtime restart/recovery outcome;
- integrate memory/PSS/available-memory and thermal snapshots through existing resource observability rather than duplicating capture;
- define retention/bounds for any newly retained event;
- keep prompt, generated output, private paths, signed URLs and native exception text out of normal telemetry.

### Acceptance

- one inference can be reconstructed chronologically from privacy-safe events;
- cold/warm load, queue delay, generation time and cleanup can be distinguished;
- failure and recovery can be correlated to the originating request;
- new metrics have explicit units, availability semantics and retention bounds;
- existing diagnostics remain the presentation owner.

## RA-8 — Device-aware execution policy

### Problem

Local-AI defaults must be selected from device evidence rather than hard-coded desktop assumptions, while avoiding unstable self-tuning behavior.

### Required changes

- introduce a pure planning boundary that consumes device capability, model profile, current resource state and requested workload;
- return an explicit backend execution plan containing only supported choices such as context budget, thread/batch policy and other currently implemented knobs;
- separate policy from backend mechanism;
- begin with conservative deterministic policies and version them;
- use measured thermal/memory evidence as inputs only when reliability and sampling semantics are defined;
- distinguish peak benchmark behavior from sustained behavior;
- record the effective policy/version and resulting execution plan in privacy-safe run metadata;
- keep unsupported GPU/speculative/multimodal choices outside this milestone.

### Acceptance

- the planner is deterministic for identical inputs;
- policy tests do not require Android or llama.cpp;
- effective runtime choices are observable and reproducible;
- memory-pressure and thermal states cannot silently select an unvalidated configuration;
- representative-device evidence is required before a policy is promoted from conservative/default to measured.

## RA-9 — Artifact and execution identity hardening

### Problem

The repository already uses SHA-256 content-addressed model identity and verified installation. Reproducible benchmarking and support claims additionally need to identify the exact execution configuration without corrupting the meaning of immutable artifact identity.

### Required changes

- preserve artifact digest as the canonical model-artifact identity;
- define a separate execution identity that can compose artifact digest, quantization/metadata, tokenizer/chat-template identity where applicable, backend/runtime version, model profile/preset and execution-policy version;
- keep model display names outside identity semantics;
- audit installation interruption, duplicate import, verification and atomic publication invariants instead of replacing the current model store;
- persist only the minimum durable execution identity needed by benchmark/evaluation/evidence owners;
- make support/evaluation evidence fail closed when required identities do not match.

### Acceptance

- two runs with materially different runtime/preset/policy configurations cannot accidentally share the same reproducibility identity;
- changing presentation metadata does not change artifact identity;
- interrupted or failed installation cannot publish an ambiguous installed artifact;
- benchmark/model-evaluation evidence can point to exact identities without storing model bytes or sensitive prompts.

## RA-10 — Backend contract and resilience testkit

### Problem

Backend replaceability is only real when every backend implementation must satisfy the same lifecycle and failure contract.

### Required changes

- create one reusable backend conformance suite driven by the SPI;
- run it against the deterministic fake and llama.cpp adapter where the test layer permits;
- cover load/release, repeated load/unload, session/context lifecycle, streaming, Unicode, stop/cancel behavior, malformed requests, long/boundary context, close during generation and deterministic error mapping;
- add resilience cases from RA-4 and queue/cancellation cases from RA-5;
- keep pure native C++ tests for native-only ownership and sampler/generation behavior;
- keep Kotlin bridge tests for JNI contract mapping;
- separate simulated acceptance from physical real-GGUF certification.

### Acceptance

- a backend cannot be considered compatible until the shared conformance suite passes;
- fake and real adapters expose the same public lifecycle semantics;
- regression tests fail on resource leaks/state corruption caused by injected failures where measurable;
- test fixtures remain deterministic and do not require network access.

## RA-11 — Security and supply-chain reproducibility

### Problem

A local-first reference must make its privacy boundary and build/model provenance verifiable rather than implied.

### Required changes

- preserve a local data plane for prompts, generated content and inference execution;
- keep optional network control-plane concerns such as catalog/download separated and explicit;
- verify model provenance, digest and compatibility before publication/use according to existing model-store policy;
- audit Gradle dependency verification and pinned native/backend/toolchain inputs;
- add or strengthen SBOM/provenance output for release artifacts where practical;
- pin or otherwise integrity-protect high-trust CI dependencies/actions according to repository release policy;
- record app/runtime/backend/NDK-relevant versions in build/evidence metadata without exposing secrets;
- retain same-signer and least-authority boundaries for shared runtime/Consumer API access;
- add security regression tests for path/URI/error/log leakage where boundaries change.

### Acceptance

- local inference does not gain an undeclared network dependency;
- model and application artifacts are traceable to verified identities;
- release evidence records the tool/runtime/backend versions required to reproduce the build;
- diagnostics remain privacy-safe under normal and failure paths;
- security-sensitive dependency changes are visible to repository validation/review.

## RA-12 — Reference-grade certification

### Goal

Close the hardening program with evidence that the architecture properties hold together, not merely in isolated unit tests.

### Required changes

- run the complete simulated lifecycle through the fake backend and production runtime core;
- run backend conformance and native tests;
- run architecture fitness, formatting, static analysis, Android lint/build and packaging gates;
- run representative physical-device real-GGUF lifecycle, cancellation, memory stability, latency/throughput and thermal evidence;
- include shared-runtime process-death/reconnect evidence where the shared path is in scope for the claim;
- verify failure recovery and repeated lifecycle operations across multiple runs;
- reconcile architecture, ADR, API, roadmap, current-state and Definition of Done documentation;
- publish only claims supported by the exact evidence level achieved.

### Acceptance

Reference-grade completion requires all P0 architecture boundaries integrated, no unowned P0 temporary exception, all required automated gates green on cumulative `dev`, and representative physical-device evidence for any production-readiness/performance claim.

## Dependency and parallelization model

```text
RA-0
 |----> RA-1 ----> RA-10 ----------------------|
 |        |                                      |
 |        +----> RA-4 --------------------|      |
 |                                      | |      |
 +----> RA-2 ----> RA-3 ----> RA-5 -----+-+--> RA-6
 |                    |                 |
 |                    +----> memory/residency integration
 |
 +----> RA-7 --------------------> RA-8
 |
 +----> RA-9
 |
 +----> RA-11

RA-1..RA-11 applicable gates -----------------> RA-12
```

The graph defines integration dependencies, not a requirement to serialize all implementation work.

### Parallel lane A — Architecture core

Sequence: RA-0 -> RA-1; RA-2 may begin after RA-0 on disjoint internals, then converges with RA-1; RA-3 integrates after the runtime ownership boundary is stable.

### Parallel lane B — Quality and resilience

RA-10 testkit scaffolding starts as soon as RA-1 exposes the backend contract. RA-4 fault/recovery work integrates after RA-3 lifecycle semantics exist. RA-5 scheduler tests can proceed in parallel with RA-4 once RA-3 is stable.

### Parallel lane C — Observability and device policy

RA-7 correlation/schema work starts after RA-0 and expands as each milestone adds lifecycle phases. RA-8 planning can be designed in parallel, but measured policies cannot integrate until RA-7 and representative memory/thermal evidence are reliable.

### Parallel lane D — Identity, integrity and supply chain

RA-9 and RA-11 can start immediately after RA-0 because they are largely disjoint from runtime decomposition. They must reuse existing model-store/security ownership rather than introduce parallel systems.

### Parallel lane E — Shared runtime

RA-6 audit/design can begin early, but behavior-changing integration waits for RA-3/4/5 semantics so Binder does not freeze an unstable runtime lifecycle contract.

### Existing memory lane

Memory/RAM-residency work continues independently where ownership is disjoint. Integration points are explicit:

- RA-3 owns lifecycle/unload transition semantics;
- RA-5 owns authoritative idle/active scheduler state used by warm-idle policy;
- RA-7 owns memory/residency observability integration;
- RA-8 consumes validated memory/thermal evidence for execution planning;
- RA-12 verifies repeated real-device memory stability.

## Incremental delivery strategy

### Tranche 1 — Enforce the seams

Run in parallel where ownership is disjoint:

- RA-0 architecture fitness baseline;
- RA-1 backend SPI + deterministic fake;
- RA-7 correlation-contract baseline;
- RA-9 execution-identity audit/design;
- RA-11 supply-chain/privacy-boundary audit.

Exit gate: dependency direction is enforceable, the fake can drive runtime tests, and no tranche introduces behavior changes without tests.

### Tranche 2 — Stabilize the runtime kernel

- RA-2 incremental orchestrator decomposition;
- RA-3 lifecycle/resource state machines;
- RA-10 initial backend conformance suite.

Exit gate: runtime state has one owner, lifecycle transitions are explicit, and memory/residency work has one integration path.

### Tranche 3 — Make failure and load behavior deterministic

Run RA-4 and RA-5 in parallel after RA-3; expand RA-10 with the new failure/scheduler cases and RA-7 with corresponding telemetry.

Exit gate: saturation, cancellation and injected failures have deterministic bounded outcomes and recovery evidence.

### Tranche 4 — Harden platform boundaries

- RA-6 shared-runtime protocol hardening;
- RA-8 evidence-driven device execution policy;
- complete RA-9/RA-11 gaps discovered by earlier tranches.

Exit gate: process, device and artifact boundaries preserve the same runtime semantics and identity.

### Tranche 5 — Certify

RA-12 executes the cumulative automated and physical evidence matrix and reconciles documentation.

## Change and branch discipline

- each implementation slice starts from the latest green `dev`;
- prefer small focused branches/PRs per milestone slice rather than one long-lived hardening branch;
- parallel branches must own disjoint files/contracts where possible;
- when one lane consumes a contract from another, wait for that contract to integrate into `dev`, then refresh before implementation;
- every behavior-changing slice adds targeted tests before moving to the next dependent slice;
- do not mark a milestone complete until cumulative `dev` remains green;
- update the focused progress tracker in the same change that changes status, blocker, dependency or next slice;
- update `architecture.md` or add an ADR only when the durable boundary/decision changes;
- keep roadmap summaries capability-level and avoid copying detailed task lists into them.

## ADR triggers

Create or update an ADR when a milestone makes a durable choice that is expensive to reverse, including:

- backend SPI ownership/module placement;
- runtime kernel state ownership model;
- public lifecycle state semantics;
- queue admission/fairness/backpressure policy exposed to consumers;
- Consumer API/Binder compatibility behavior;
- execution-policy versioning semantics;
- reproducibility identity composition;
- release/provenance requirements that constrain downstream consumers.

Routine refactors that preserve accepted boundaries do not require a new ADR.

## Validation model

Every slice follows the repository Definition of Done. At minimum:

- focused unit/contract tests at the lowest useful layer;
- failure, cancellation and cleanup paths when relevant;
- formatter/static analysis and affected compilation;
- architecture/navigation/documentation checks when boundaries/docs change;
- native C++ and JNI bridge checks for native changes;
- Android packaging checks for ABI/loading changes;
- cumulative pull-request and `dev` validation;
- representative physical-device evidence before any production-readiness, memory-stability or performance claim.

The progress tracker records implementation/evidence status; this target document remains stable unless the intended architecture or acceptance criteria change.
