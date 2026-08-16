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
- `PARTIAL`: meaningful behavior exists, but target architecture/tests/evidence remain.
- `DEVICE`: implementation/automation are integrated; representative device evidence remains.
- `PENDING`: focused target work has not started.

## Milestone status

| ID | Status | Current boundary | Remaining gate | Depends on |
| --- | --- | --- | --- | --- |
| RA-0 Architecture fitness | PARTIAL | Canonical Gradle inventory and dependency/native fitness rules are implemented and under full validation | Integrate guards, remove the native `.cpp` include debt, then delete its self-expiring exception | None |
| RA-1 Backend SPI/fake | PARTIAL | `core:backend-spi`, store-neutral `BackendModelSource`, llama.cpp adapter ownership and deterministic fake are implemented; runtime-core no longer needs a concrete backend dependency in the slice | Complete shared-contract validation, integrate after RA-0, remove the dependency-debt exception and confirm cumulative `dev` green | RA-0 |
| RA-2 Runtime kernel | PENDING | Centralized rich orchestration exists | One state owner plus focused collaborators without distributed lifecycle state | RA-0; converge with RA-1 |
| RA-3 Lifecycle/state machines | PARTIAL | load/generate/cancel/release/shutdown and pressure behavior exist | Code/test transition tables, ownership matrix, idempotent terminals, one residency path | RA-2 |
| RA-4 Failure/recovery | PARTIAL | Typed privacy-safe failures/recovery exist in several paths | Cross-layer recovery matrix + deterministic fault/race injection | RA-1, RA-3 |
| RA-5 Scheduling/backpressure | PARTIAL | Single decode, priority and cancellation exist | Queue/buffer bounds, admission/fairness, slow-consumer and cancellation-latency semantics | RA-3 |
| RA-6 Shared runtime | PARTIAL | SR/Binder/Consumer API lifecycle behavior is substantial | Close verified idempotency/death/reconnect/orphan-cleanup gaps against final runtime semantics | RA-3/4/5 |
| RA-7 Observability | PARTIAL | Correlated telemetry/resources/benchmarks/timelines exist | Standard lifecycle correlation including recovery/cancel/residency reasons | RA-0; continuous |
| RA-8 Device policy | PARTIAL | Compatibility, profiles, memory/thermal capture and tuning work exist | Pure versioned planner using validated device/model/resource inputs | RA-7 + evidence |
| RA-9 Execution identity | PARTIAL | SHA-256 artifact + evaluation identities exist | Separate reproducible execution identity spanning material runtime/config/policy inputs | RA-0 |
| RA-10 Backend testkit | PARTIAL | Native/JNI/simulated acceptance plus deterministic SPI fake coverage exist | Reusable SPI conformance suite shared by fake/llama.cpp and expanded for RA-3/4/5 | RA-1 |
| RA-11 Security/provenance | PARTIAL | Secure download, signing/versioning, packaging and privacy rules exist | Close verified provenance/SBOM/dependency-integrity gaps | RA-0 |
| RA-12 Certification | PENDING | Existing evidence tooling covers several gates | Cumulative architecture/resilience/packaging/device evidence | Applicable RA-1..11 |

## Active architecture debt

The first hardening slices make architecture debt explicit instead of relying on prose:

- native source still includes `llama_jni.cpp` from `llama_jni_entry.cpp`; RA-0 native cleanup must convert this to normal translation-unit linkage before RA-0 can be `DONE`;
- RA-1 dependency inversion is not complete until its validated implementation is integrated after RA-0 and the temporary `runtime-core -> backends:llama-cpp` exception is removed from the fitness rule;
- `RuntimeOrchestrator` responsibility/mutation seams, queue/slow-consumer bounds, lifecycle idempotency, failure recovery, shared-runtime death semantics and execution-identity gaps remain discovery inputs for later milestones.

The RA-1 target boundary is recorded in [ADR 0014](adr/0014-backend-spi-boundary.md): backend SPI is separate from runtime orchestration and model-store persistence, while runtime adapts an already verified stored model to a backend-neutral source.

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

RA-2 inventory/extraction preparation may run beside RA-1 after RA-0; its final backend gateway consumes the integrated RA-1 contract.

## Parallel lanes

| Lane | Start | Scope | Integration constraint |
| --- | --- | --- | --- |
| A Architecture core | Active | Finish RA-0 -> integrate RA-1; RA-2 discovery can continue | RA-3 waits for stable state owner |
| B Quality/resilience | After RA-1 contract | RA-10; later RA-4 and RA-5 in parallel | RA-4/5 consume RA-3 semantics |
| C Observability/device | Now | RA-7 audit; RA-8 design | Measured RA-8 waits for resource/device evidence |
| D Identity/security | Now | RA-9 + RA-11 audits | Reuse existing owners/stores |
| E Shared runtime | Audit now | RA-6 gap analysis | Behavior waits for RA-3/4/5 |
| Existing memory | Continues | Residency/pressure/warm-idle | Converge through RA-3/5/7/8 |

## Current implementation block

1. **RA-0 fitness integration:** validate canonical module discovery and architecture guards on the complete repository; retain only exact named debt exceptions.
2. **RA-0 native cleanup:** replace implementation `.cpp` inclusion with normal CMake-linked translation units and delete the corresponding fitness exception.
3. **RA-1 SPI integration:** validate `core:backend-spi`, `BackendModelSource`, runtime-store adaptation, llama.cpp implementation ownership and deterministic fake coverage.
4. **RA-1 permanent dependency gate:** integrate on top of RA-0 and delete the `runtime-core -> backends:llama-cpp` exception so any recurrence fails CI.
5. **RA-10 seed:** convert the reusable deterministic fake and existing backend/native tests into the first shared conformance cases once RA-1 is integrated.

Exit: runtime-core builds/tests without llama.cpp implementation, fake drives deterministic lifecycle tests, real application composition remains compatible, architecture rules fail on regression, and native implementation units are linked normally.

## Parallel tranche-1 discovery

While RA-0/RA-1 integrate on disjoint ownership:

- **RA-2A:** map runtime state, mutation points and extraction seams; no distributed state owner.
- **RA-7A:** map correlation/lifecycle phases and propose additions only in existing observability ownership.
- **RA-9A:** map artifact/profile/benchmark/evaluation identities and isolate the minimum missing execution identity.
- **RA-11A:** audit dependency verification, toolchain/action pins, release metadata, signing and provenance before adding mechanisms.
- **RA-6A:** audit existing protocol version/capability/identity/death/reconnect behavior and record verified gaps only.

Each discovery slice ends with an implementation-ready gap list or `no change required`; naming a concept in the target is not justification to create architecture.

## Memory coordination

Current memory work continues: keep one residency owner; represent unload causes explicitly; let scheduler state become authoritative for idle decisions; use existing resource/telemetry owners; treat device PSS/thermal data as evidence rather than universal thresholds; migrate through RA-3/RA-5 contracts without unnecessary user-visible change.

## Update and completion rule

On status/blocker/dependency/next-slice change, update this tracker in the same change. Update `current-state.md` only for repository-wide operational state, `roadmap.md` only for capability outcomes, the target only for intent/acceptance changes, and `architecture.md`/ADR only for durable boundaries/decisions.

A milestone is `DONE` only after integration into `dev`, targeted acceptance, applicable regression guards, failure/cancel/cleanup coverage, required docs, cumulative green `dev`, and required physical evidence. Use `DEVICE` when implementation is integrated but representative hardware evidence remains. The workstream closes only after RA-12 removes/owns all P0 exceptions and demonstrates the reference-grade properties.
