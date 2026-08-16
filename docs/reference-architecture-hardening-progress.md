# Local AI reference architecture hardening progress

Status: active
Document type: workstream-state
Owner: repository
Canonical scope: state.reference-architecture-hardening
Read when: determining current reference-architecture hardening status, dependencies or the next implementation slice
Last reviewed: 2026-08-16

Canonical target: [`reference-architecture-hardening-plan.md`](reference-architecture-hardening-plan.md)

This tracker owns concise implementation state only. The repository-wide integrated baseline and immediate repository priorities remain in [`current-state.md`](current-state.md); capability-level outcomes remain in [`roadmap.md`](roadmap.md).

The hardening plan was created from `dev` commit `0a3ae6382e752d2eae49cc5379be778cb76ea2e1`. Every implementation branch must start from the latest green `dev` available when that slice begins.

## Status legend

- `DONE`: target behavior, automated acceptance criteria and required documentation are integrated; required evidence is present.
- `PARTIAL`: meaningful existing behavior covers part of the target, but architecture, tests or evidence remain.
- `DEVICE`: implementation and automated gates are integrated; representative physical-device evidence remains.
- `PENDING`: the focused target slice has not started.

## Milestone status

| ID | Workstream | Status | Current boundary | Remaining gate | Depends on |
| --- | --- | --- | --- | --- | --- |
| RA-0 | Architecture baseline and fitness rules | PARTIAL | Documentation and Definition of Done define modular/native rules; repository validation exists | Executable dependency/native fitness rules; remove or explicitly own baseline violations; derive CI module scope from canonical build metadata | None |
| RA-1 | Backend SPI and deterministic fake | PENDING | Runtime uses backend abstraction internally but `core:runtime-core` still has a concrete `:backends:llama-cpp` dependency | Stable backend SPI, real adapter behind backend module, deterministic fake, runtime-core free of llama.cpp implementation dependency | RA-0 |
| RA-2 | Runtime kernel decomposition | PENDING | Runtime orchestration is centralized and behaviorally rich | Single authoritative kernel plus focused collaborators without distributed mutable lifecycle state | RA-0; converges with RA-1 |
| RA-3 | Lifecycle and resource state machines | PARTIAL | Explicit load/generate/cancel/release/shutdown and memory-pressure behavior exist | Code/test-owned legal transition tables, complete ownership matrix, idempotent terminal operations and one residency path | RA-2 integration boundary |
| RA-4 | Failure, recovery and fault injection | PARTIAL | Typed privacy-safe failures and recovery behavior exist in several paths | Cross-layer recovery matrix plus deterministic injected failure/race coverage | RA-1, RA-3 |
| RA-5 | Scheduling, backpressure and cancellation | PARTIAL | Single active decode, request priority and queued/active cancellation exist | Explicit queue/buffer bounds, admission/fairness policy, slow-consumer semantics and cancellation latency evidence | RA-3 |
| RA-6 | Shared-runtime protocol hardening | PARTIAL | SR-0..SR-5 and substantial Binder/Consumer API lifecycle behavior are integrated | Audit and close idempotency/deadline/client-death/orphan-cleanup gaps against final runtime semantics | RA-3, RA-4, RA-5 |
| RA-7 | End-to-end observability contract | PARTIAL | Request-correlated telemetry, health, resources, benchmark and timelines exist | One standardized correlation/lifecycle phase contract including recovery, cancellation and residency reasons | RA-0; expands continuously |
| RA-8 | Device-aware execution policy | PARTIAL | Device compatibility, Qwen profiles, memory/thermal capture and device tuning work exist | Pure versioned execution planner using validated device/model/resource inputs; measured policy evidence | RA-7 plus device/memory evidence |
| RA-9 | Artifact and execution identity hardening | PARTIAL | SHA-256 content-addressed model identity, verified install and evaluation identities exist | Separate reproducible execution identity spanning artifact/config/backend/policy without changing artifact identity | RA-0 |
| RA-10 | Backend contract and resilience testkit | PARTIAL | Native tests, Kotlin bridge tests and simulated runtime acceptance exist | Reusable SPI conformance suite shared by fake and llama.cpp adapters, expanded with lifecycle/failure/scheduler cases | RA-1; expands with RA-3/4/5 |
| RA-11 | Security and supply-chain reproducibility | PARTIAL | Secure model download, signing/versioning, packaging checks and privacy rules exist | Close provenance/SBOM/dependency-integrity gaps and verify local-data-plane invariants across failures | RA-0 |
| RA-12 | Reference-grade certification | PENDING | Existing device/evidence infrastructure can supply several required gates | Cumulative architecture, resilience, packaging and representative physical-device evidence against the final boundaries | RA-1 through RA-11 as applicable |

## Baseline architecture debt to close

The planning baseline contains two concrete enforceable examples:

- `core/runtime-core/build.gradle.kts` declares `implementation(project(":backends:llama-cpp"))`, so runtime policy is not yet backend-implementation independent;
- `backends/llama-cpp/src/main/cpp/llama_jni_entry.cpp` includes `llama_jni.cpp`, despite the Definition of Done requiring native implementation units to be linked normally rather than included from another `.cpp`.

RA-0 must turn both categories into executable regression guards. RA-1 owns the Gradle dependency correction; RA-0/native cleanup owns normal CMake translation-unit linkage.

Additional debt to verify before implementation rather than assume from documentation:

- exact `RuntimeOrchestrator` responsibility/complexity inventory and extraction seams;
- queue capacity and slow-consumer bounds across JNI, Kotlin and Binder paths;
- lifecycle idempotency coverage for every terminal resource operation;
- failure-to-recovery coverage by phase;
- existing shared-runtime version/capability/death semantics before proposing protocol changes;
- reproducibility fields already owned by benchmark/model-evaluation stores before adding execution identity data;
- existing Gradle/CI dependency-integrity and provenance coverage before adding new supply-chain mechanisms.

## Dependency routing

Immediate integration dependencies:

```text
RA-0 -> RA-1 -> RA-10
RA-0 -> RA-2 -> RA-3 -> RA-4
                    \-> RA-5
RA-3 + RA-4 + RA-5 -> RA-6
RA-0 -> RA-7 -> RA-8
RA-0 -> RA-9
RA-0 -> RA-11
applicable RA-1..RA-11 -> RA-12
```

RA-2 internal inventory/extraction preparation may run in parallel with RA-1 after RA-0, but the final backend gateway seam must consume the integrated RA-1 contract.

## Parallel work lanes

| Lane | Can start | Work | Integration constraint |
| --- | --- | --- | --- |
| A — architecture core | Now | RA-0, then RA-1; RA-2 inventory/extraction prep in parallel | RA-3 waits for one stable runtime state owner |
| B — quality/resilience | After RA-1 contract exists | RA-10 scaffolding; later RA-4 and RA-5 in parallel | RA-4/5 consume RA-3 lifecycle semantics |
| C — observability/device | Now | RA-7 contract audit; RA-8 planner design | Measured RA-8 policy waits for validated resource/device evidence |
| D — identity/security | Now | RA-9 identity audit and RA-11 provenance/privacy audit | Reuse current model/evaluation/security owners; no parallel stores |
| E — shared runtime | Design audit can start now | RA-6 gap analysis | Behavior changes wait for RA-3/4/5 to avoid freezing unstable semantics |
| Existing memory lane | Continues independently | RAM residency, memory-pressure and warm-idle work | Must converge through RA-3, RA-5, RA-7 and RA-8 contracts |

## Recommended first implementation block

The first hardening block should remain narrow enough to merge incrementally and should not wait for unrelated OMBRA/model-evaluation/device work when file ownership is disjoint.

### RA-0A — Baseline architecture map

- enumerate current Gradle module edges relevant to runtime/backend/apps/transport;
- enumerate JNI/CMake implementation-unit ownership;
- identify existing architecture validation scripts before adding a new mechanism;
- record only real exceptions and assign each to a removal milestone.

Exit: reviewed dependency/ownership map with no speculative target modules.

### RA-0B — Native and dependency fitness tests

- add a local/CI guard for forbidden `.cpp` implementation includes;
- add a local/CI guard for forbidden module dependency edges;
- eliminate duplicate module inventories where canonical Gradle metadata can drive validation;
- prove each guard with a focused regression fixture/test.

Exit: both guard classes fail deterministically when violated.

### RA-1A — Backend contract extraction

- inventory the exact operations/runtime data used from the current llama.cpp adapter;
- define the minimal backend-facing contract without JNI/native types;
- choose the contract owner according to the Definition of Done; create a new module only if it immediately owns a necessary dependency boundary;
- add isolated contract tests.

Exit: stable minimal contract accepted and used by at least the fake/real composition work, not an empty abstraction.

### RA-1B — Deterministic fake

- implement deterministic load/stream/cancel/release behavior;
- expose controllable timing and failure hooks needed by later RA-4 tests without embedding recovery policy in the fake;
- migrate simulated runtime acceptance to the SPI where practical.

Exit: runtime behavior can be tested without JNI/llama.cpp.

### RA-1C — Invert the real dependency

- move/retain llama.cpp adaptation entirely inside `backends:llama-cpp`;
- inject it at application/runtime composition roots;
- remove the concrete backend implementation dependency from `core:runtime-core`;
- activate the RA-0 fitness rule as a permanent gate.

Exit: runtime-core builds/tests without llama.cpp implementation on its graph and connected applications still compose the production adapter.

## Parallel discovery slices for tranche 1

These can proceed while RA-0/RA-1 are implemented, provided they do not change shared contracts prematurely:

- **RA-7A:** inventory current request/session/model correlation and missing lifecycle phases; propose additions only in existing observability ownership.
- **RA-9A:** map artifact, installed-model, model-profile, benchmark and evaluation identities; identify the minimum missing execution-identity composition.
- **RA-11A:** audit current dependency verification, pinned actions/toolchains, release metadata, signing and model provenance before adding mechanisms.
- **RA-2A:** map `RuntimeOrchestrator` state, collaborators, methods and mutation points; identify extraction seams that preserve one state owner.
- **RA-6A:** audit existing shared-runtime/Consumer API version, capability, identity, death and reconnect semantics; report only verified gaps.

Each discovery slice ends in either a small implementation-ready gap list or `no change required`; it must not create architecture solely because the target document names a concept.

## Memory-workstream coordination

Do not pause current memory hardening merely because RA-2/RA-3 are not complete. Use these coordination rules:

- avoid introducing a second model-residency owner;
- express new unload causes as explicit reasons that can later enter the RA-3 state machine;
- do not let warm-idle TTL infer idleness independently if scheduler state can be the authority;
- emit new memory/residency evidence through existing resource/telemetry owners;
- keep device-specific PSS/thermal evidence as evidence, not hard-coded universal thresholds;
- when RA-3/RA-5 integrate, migrate memory decisions to the common lifecycle/scheduler contracts without changing user-visible semantics unnecessarily.

## Update rule

Whenever a hardening change alters a milestone state, blocker, dependency or next slice:

1. update this tracker in the same change;
2. update [`current-state.md`](current-state.md) only when the repository-wide integrated baseline, blocker or immediate next block changes;
3. update [`roadmap.md`](roadmap.md) only when capability-level remaining outcomes change;
4. update [`reference-architecture-hardening-plan.md`](reference-architecture-hardening-plan.md) only when target behavior, dependency sequencing or acceptance criteria change;
5. update [`architecture.md`](architecture.md) and/or an ADR when a durable boundary or decision changes;
6. keep branch/PR narratives out of the roadmap and target specification.

## Completion rule

A milestone moves to `DONE` only when:

- implementation is integrated into `dev`;
- targeted automated acceptance is green;
- architecture/contract regressions are guarded where applicable;
- relevant failure/cancellation/cleanup paths are covered;
- required documentation owners are updated;
- cumulative `dev` validation remains green;
- any required physical-device evidence is present, otherwise use `DEVICE` rather than `DONE`.

The workstream closes only when RA-12 has reconciled all remaining P0 exceptions and the repository can demonstrate the reference-grade properties defined by the target specification.
