# LLUP — llama.cpp v0.3.0 upgrade and bounded residency qualification

Status: active
Document type: target-specification
Owner: llama-cpp-runtime / runtime-memory
Canonical scope: workstream.llama-cpp-v0-3-residency
Read when: upgrading the llama.cpp production pin or evaluating bounded multi-model residency
Base at plan creation: `dev@ace38169d4aba87540ca8d61ec4effee7aff28c5`
Last reviewed: 2026-08-30

## Goal

Move the Android Local LLM Harness from the current production `llama.cpp` pin `b9637` / `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3` to a newly qualified stable upstream baseline, with `v0.3.0` / `c1d0e7a004015f23bc0233470b747b596f29b264` as the first production candidate.

The upgrade must preserve backend-neutral contracts, deterministic lifecycle ownership, Android ARM64 portability, bounded memory behavior and existing evidence semantics. It must also produce the evidence needed to decide whether the runtime can safely support more than one resident model on qualified device/profile combinations without enabling simultaneous production decode.

The latest nightly may be evaluated as a diagnostic comparison candidate, not as the default production target. At plan creation the diagnostic candidate is `b10689` / `57291f2644af8c9df0dd8d44395881c5bdcf0ecd`.

## Why now

The current pin predates substantial upstream evolution in `llama.cpp`/`ggml`. The repository already owns runtime-memory, execution-identity and physical-evidence infrastructure, so the upgrade can be measured instead of treated as a blind dependency refresh.

The existing [`../llama-cpp-runtime-optimization-plan.md`](../llama-cpp-runtime-optimization-plan.md) remains the canonical technical target for backend optimization. This document is the bounded execution plan for promoting a new upstream baseline and then, only if evidence supports it, extending the existing residency owner.

## Source-of-truth boundaries

- `backends/llama-cpp`: upstream adaptation, native mechanism, build flags, JNI implementation and backend revision.
- `core/runtime-core`: lifecycle, admission, scheduling, leases and residency policy application.
- `docs/memory-management`: aggregate budget, admission, pressure handling and physical memory evidence.
- `docs/qwen35`: reviewed model/profile identity and device tuning evidence.
- `observability`: execution identity, performance and privacy-safe measurement.
- `evaluation`: quality and deterministic comparison where output behavior changes.
- `third_party/llama.cpp`: exact upstream source; no Harness-local fork or ad-hoc patch is introduced by this workstream.

Existing owners are extended, not duplicated. Multi-model residency must evolve `ModelResidencyLifecycle` and the existing admission/memory policy rather than create a second loader, cache or residency manager.

## Invariants

1. Production stays on an exact immutable upstream commit; no floating `master`/nightly dependency.
2. `v0.3.0` is the first promotion candidate because it is a stable upstream release. Nightlies are diagnostic unless a later explicit decision promotes one.
3. The current `b9637` pin remains the control baseline until promotion evidence is complete.
4. No `llama.cpp`/ggml type, pointer or backend-specific structure crosses backend-neutral contracts.
5. Android portability intent remains `arm64-v8a`, `GGML_BACKEND_DL=ON`, `GGML_CPU_ALL_VARIANTS=ON`, `GGML_NATIVE=OFF`; any change is a separate evidence-backed decision.
6. OpenCL/GPU enablement is not bundled into the pin upgrade. Existing LLRT OpenCL work remains separate and default-off.
7. Production keeps one active decode. Multi-model residency does not imply multi-decode concurrency.
8. Default resident-model capacity remains `1` until representative physical evidence and an explicit policy decision qualify a larger bounded value.
9. A model with a live context/session/activation lease cannot be selected for eviction.
10. Aggregate admission must account for all resident model/context/backend state; file size alone is not a safe memory estimate.
11. Memory pressure, cancellation, switch, shutdown and partial-failure cleanup remain idempotent and converge on existing ownership.
12. Backend-revision changes invalidate affected performance/memory evidence. Historical evidence is not silently reused for the promoted pin.
13. Cross-version deterministic output equality is not assumed. Material output/digest drift is classified and quality-gated, not suppressed.
14. Promotion requires exact-HEAD automated evidence plus separately required REAL_ENVIRONMENT evidence. Writing code is not completion.

## Non-goals

- simultaneous production decodes;
- a second scheduler, model store, loader or residency subsystem;
- opportunistic OpenCL/Hexagon/HTP enablement while changing the pin;
- broadening supported model families without their own review;
- weakening OMBRA/Q35/evaluation quality gates to accommodate changed outputs;
- treating emulator/CI memory numbers as representative-device memory certification;
- making `latest nightly` the production dependency policy.

## Workstream ledger

Status vocabulary: `PLANNED`, `READY`, `IN PROGRESS`, `BLOCKED`, `DONE`, `DEFERRED`.

| ID | State | Parallel lane | Outcome / next gate |
| --- | --- | --- | --- |
| LLUP-00 | READY | foundation | Freeze exact control/target identities, upstream/API delta and affected consumer/test map. |
| LLUP-10 | READY | build identity | Make one repo-owned source authoritative for submodule/build/runtime/verifier identity. |
| LLUP-20 | BLOCKED by LLUP-00 | native migration | Move to exact `v0.3.0` and adapt CMake/JNI/native API without widening public contracts. |
| LLUP-30 | BLOCKED by LLUP-20 | correctness | Requalify generation, streaming, cancellation, model/context lifecycle, prompt reuse, cache/state and native ownership. |
| LLUP-40 | BLOCKED by LLUP-20 | automated packaging | Requalify Android ARM64 build/package/R8/lint/native artifact invariants on exact HEAD. |
| LLUP-50 | BLOCKED by LLUP-30/40 | physical A/B | Compare `b9637` vs `v0.3.0` on representative hardware with fixed model/profile/workload identity. |
| LLUP-60 | BLOCKED by LLUP-30/50 | quality/evidence | Replay affected Q35, memory, OMBRA/evaluation and execution-identity evidence. |
| LLUP-70 | BLOCKED by LLUP-60 | promotion | Promote `v0.3.0`, retain `b9637`, or investigate a newer exact nightly from classified evidence. |
| MRES-00 | READY after LLUP-00 | residency design | Define bounded multi-resident semantics as an extension of `ModelResidencyLifecycle`; default remains capacity `1`. |
| MRES-10 | BLOCKED by LLUP-70/MRES-00 | residency implementation | Implement keyed bounded residency with aggregate admission and deterministic lease-safe eviction. |
| MRES-20 | BLOCKED by MRES-10 | automated correctness | Prove lifecycle, switch, lease, pressure, cleanup and capacity-1 compatibility. |
| MRES-30 | BLOCKED by MRES-20 | physical qualification | Measure single-resident vs bounded two-resident scenarios and decide whether any device/profile policy may opt in. |
| MRES-40 | BLOCKED by MRES-30 | product policy | Promote only evidence-qualified residency policies; global/default capacity remains 1 unless separately approved. |

## Dependency and parallelization graph

```text
LLUP-00
  |\
  | +--------------------> MRES-00  [design only]
  v
LLUP-10
  |
  v
LLUP-20
  |\
  | +--> LLUP-40
  v
LLUP-30
  |\
  | +--> LLUP-50
  |        |
  +------> LLUP-60
             |
             v
          LLUP-70
             |
             +-------------> MRES-10 -> MRES-20 -> MRES-30 -> MRES-40
```

Parallel work is encouraged where ownership does not overlap:

- LLUP-10 pin identity and MRES-00 contract/design can proceed after LLUP-00.
- After LLUP-20 compiles, LLUP-30 correctness and LLUP-40 Android packaging can run in parallel.
- Physical LLUP-50 waits for an exact automated candidate; physical runs are serialized per device/thermal window.
- MRES implementation waits until the backend promotion decision is stable so residency is not debugged against a moving native baseline.

## LLUP-00 — Baseline and delta inventory

### Scope

- freeze `dev` base, production control pin, stable target pin and optional diagnostic nightly pin;
- compare upstream changes from `aedb2a5...` to `c1d0e7a...`;
- identify CMake, public C API and behavior changes consumed by Harness;
- inspect direct JNI/native consumers and tests before editing shared contracts;
- record known migration hazards already found by LLRT qualification, including sampler vocabulary requirements and model load-mode API evolution;
- identify evidence identities that include backend revision and become stale after promotion.

### Acceptance

- exact SHAs are recorded;
- affected modules/consumers/tests are enumerated;
- no unexplained public-contract change is required;
- validation selector input is complete before native edits begin.

## LLUP-10 — Single authoritative backend pin identity

The repository currently carries pin knowledge in more than one build/verification location. A pin change must not allow the submodule SHA, CMake revision metadata, runtime diagnostics and verification script to diverge.

Introduce one repository-owned authoritative representation of the `llama.cpp` dependency identity and make build/runtime/verifier paths consume or verify it. The exact mechanism is chosen during implementation after checking scoped guidance; do not introduce an abstraction without a real owner.

Acceptance:

- submodule SHA and declared backend revision cannot silently disagree;
- runtime execution identity reports the exact promoted upstream revision;
- repository verification fails closed on mismatch;
- pin maintenance does not require independent magic strings across scripts.

## LLUP-20 — Mechanical migration to `v0.3.0`

### Scope

- update `third_party/llama.cpp` to `c1d0e7a004015f23bc0233470b747b596f29b264`;
- adapt native/JNI code to upstream API changes;
- preserve backend-neutral Kotlin contracts unless a material incompatibility makes a change unavoidable;
- keep portable Android CPU variant selection and existing package ownership;
- do not add local upstream patches unless a separately documented blocking defect requires one.

### Migration checks

- sampler construction / vocabulary requirements;
- model loading and effective load-mode mapping;
- model/context parameter defaults changed upstream;
- KV/Flash-Attention enum/default compatibility;
- tokenization, decode/logits and sequence APIs used by normal and evaluation-batch paths;
- native handle/cancellation registries and destruction order;
- dynamic backend discovery and packaged shared-library names;
- compiler/NDK/CMake warnings or changed transitive requirements.

Acceptance requires host-native qualification where owned by repository automation, Android ARM64 native compilation, no backend type leakage and no unclassified native package delta.

## LLUP-30 — Correctness and lifecycle requalification

Re-run focused contract/native/runtime suites for:

- model inspect/load/unload/reload;
- context create/release/recreate;
- generation and fixed-seed within-version replay;
- streaming aggregation and cancellation;
- prompt/token preparation reuse and fallback;
- sampler/grammar/penalty behavior used by production profiles;
- KV-cache/Flash-Attention materialization with current defaults;
- evaluation-only multi-sequence path without broadening production concurrency;
- model switch and A -> B -> A reuse;
- pressure/shutdown cleanup and repeated close semantics;
- execution fingerprint/backend revision propagation.

If `v0.3.0` changes an existing fixed-identity output digest, classify the cause. Do not alter a legitimate quality gate merely to recover the old digest.

## LLUP-40 — Android build/package validation

Because this slice touches native/JNI/dependency/build/package boundaries, expected validation depth is **STRONG** unless the repository selector escalates to FULL.

Required deterministic evidence is repository-owned and runs locally with an equivalent environment, otherwise through REMOTE_AUTOMATED preflight. It is not delegated to the user solely because Android SDK/NDK tooling is unavailable to the agent.

At minimum cover the selector-required subset of:

- formatting/static analysis;
- affected Kotlin/unit/contract tests;
- host-native tests;
- Android compile and lint;
- CMake/NDK ARM64 build;
- selected debug/release assembly and R8/ProGuard/package checks;
- expected native `.so` inventory and ABI constraints;
- dependency/model-artifact guards;
- exact backend revision in build/runtime evidence.

## LLUP-50 — Physical same-device A/B

This evidence is **REAL_ENVIRONMENT** because mobile memory, thermal and runtime behavior require representative hardware. Automated build/package gates remain separate and deterministic.

Use the same device, exact GGUF, quantization, Harness profile, context/output limits, prompt/workload identity and thermal-start policy.

```text
control:   b9637 / aedb2a5...
candidate: v0.3.0 / c1d0e7a...
optional diagnostic: b10689 / 57291f2...
```

Record at least:

- model load latency;
- TTFT / first-token latency;
- prompt/prefill and decode throughput;
- end-to-end latency;
- process PSS baseline, peak, post-release residual and repeated-cycle trend;
- native/Java heap where current evidence tooling exposes them;
- available memory and low-memory state;
- model/context resident counts;
- context/KV configuration identity;
- warm-idle behavior;
- A -> B -> A switch behavior;
- cancellation and prepare-after-release recovery;
- thermal status at controlled checkpoints;
- backend/execution fingerprint and exact source identities.

Do not invent a percentage threshold in this plan. Apply existing repository guardrails. If promotion needs a threshold not already owned by canonical policy, record it as an explicit decision derived from baseline evidence rather than choosing it ad hoc.

## LLUP-60 — Affected quality/evidence replay

A new backend revision changes execution identity. Replay only evidence whose validity materially depends on that revision, while never treating stale evidence as current.

Expected affected lanes include:

- Q35 model/profile qualification relevant to promoted defaults;
- memory cost/profile and lifecycle evidence;
- OMBRA/evaluation quality runs whose generated outputs depend on backend behavior;
- LLRT fixed-seed/cache/batch evidence used to justify active policies;
- release/package identity where the backend binary is part of the promoted artifact.

Use existing dataset/policy thresholds. A backend upgrade must not lower pre-registered support criteria.

## LLUP-70 — Promotion decision

Promotion is a deliberate decision, not the successful completion of a submodule update.

Possible outcomes:

1. **PROMOTE_V0_3_0** — automated exact-HEAD gates pass and required physical/quality evidence is acceptable.
2. **KEEP_B9637** — the candidate has a classified unacceptable regression; retain the current production pin and record the blocker.
3. **DIAGNOSE_NEWER_UPSTREAM** — a newer nightly demonstrably addresses the blocker; qualify that exact commit rather than floating to latest.

Promotion invalidates affected old exact-head evidence and updates durable current state, LLRT target status, backend identity documentation and release/evidence references that claim the old production pin.

## MRES-00 — Bounded multi-resident contract

This design lane may start after LLUP-00, but implementation waits for the backend promotion decision.

Required semantics:

- `ModelResidencyLifecycle` remains the canonical residency owner;
- residency becomes model-keyed/bounded rather than adding a second singleton loader;
- policy capacity is explicit and `1` remains the compatibility/default value;
- live session/context/activation leases pin their model against eviction;
- admission considers aggregate resident model/context cost plus safety reserve and current observation;
- eviction is deterministic among eligible lease-free residents;
- capacity 1 preserves existing switch semantics;
- production active decode remains globally bounded by existing scheduler policy;
- critical pressure/shutdown converges residents to release through existing idempotent cleanup ownership.

Design acceptance requires documented state/invariants, unambiguous handle/context/lease ownership, no native details in the Consumer API, a capacity-1 compatibility proof strategy and evidence-gated capacity >1.

## MRES-10/20 — Implementation and automated proof

Evolve the existing residency owner and direct consumers rather than layering compensating caches.

Automated cases include:

- capacity-1 load/switch/reuse compatibility;
- A+B resident under an explicitly larger test policy;
- lease prevents eviction;
- released idle resident becomes eligible;
- deterministic candidate selection;
- aggregate admission reject/downshift/allow behavior;
- context/model load failure rollback;
- background/warm-idle expiry;
- critical trim/pressure cleanup;
- cancellation during switch/prepare;
- repeated A -> B -> A cycles;
- shutdown with multiple residents;
- no stale native handles after terminal cleanup;
- one active production decode invariant remains intact.

Expected validation depth is **STRONG** because shared lifecycle, memory admission and native-resource ownership are material boundaries.

## MRES-30 — Physical bounded-residency qualification

On the same representative device/profile identities compare:

1. one resident model idle;
2. one resident model active with context;
3. two resident models without active contexts where policy allows;
4. model A active while model B is warm-idle;
5. A -> B -> A generation with one active decode;
6. memory pressure while two models are resident;
7. repeated prepare/release/switch cycles.

Capture peak/residual PSS, available memory, thermal state, load/switch latency, eviction/reload behavior, admission decisions and recovery. Capacity >1 is not promoted merely because both models fit once.

## Validation model

### Plan-only change

This target specification and its durable link are **LEAN** documentation/governance scope.

### Implementation

- LLUP native/JNI/dependency/build slices: expected **STRONG**, selector may escalate to FULL.
- MRES shared lifecycle/admission/resource slices: **STRONG**.
- Global build/toolchain or release/promotion scope: **FULL** only when selector or release policy requires it.

### Execution capability

- deterministic compile/test/lint/package/R8/native gates: `AGENT_LOCAL` with equivalent environment, otherwise `REMOTE_AUTOMATED` using repository-owned automation;
- physical Android memory/thermal/real-GGUF evidence: `REAL_ENVIRONMENT`;
- pending REAL_ENVIRONMENT evidence is reported separately from automated preflight readiness.

### Readiness vocabulary

- `READY_FOR_CI`: selected deterministic gates are local and passed;
- `READY_FOR_REMOTE_PREFLIGHT`: local gates pass and deterministic remote gates remain;
- `AUTOMATED_PREFLIGHT_CONFIRMED`: all required selected deterministic gates pass on exact HEAD/base;
- `NOT_READY_FOR_AUTOMATED_PREFLIGHT`: ambiguity, stale base, failed local gate, invalid scope or automation gap blocks a truthful claim.

## Evidence invalidation rules

The following invalidate affected exact-head evidence:

- upstream pin or native build-flag changes;
- JNI/native lifecycle edits;
- generation/sampler/tokenization/cache behavior edits;
- memory admission/residency policy edits;
- material rebase/merge base movement;
- R8/package/native-library composition changes relevant to the promoted artifact.

After invalidation rerun the affected required gates at the correct depth; never reuse an older green run for a newer material HEAD.

## Exit criteria

The workstream is complete only when all applicable statements are true:

- a production backend pin decision is recorded with exact identity;
- promoted pin, build metadata, verifier and runtime execution identity agree;
- selected deterministic validation is confirmed on exact HEAD/base;
- required representative-device Q35/memory/quality evidence for the promoted backend is recorded;
- no legitimate quality gate was weakened to accommodate the upgrade;
- multi-model residency either remains explicitly deferred/default-1 or has bounded STRONG automated evidence plus device/profile-scoped physical qualification;
- one-active-production-decode remains invariant unless a later independent workstream changes it;
- canonical current-state/LLRT/memory/release documentation is reconciled;
- obsolete temporary branches/evidence coordinators for this wave are cleaned up after integration.

## First execution wave

Start with these tasks, maximizing safe parallelism:

1. **LLUP-00** — exact upstream delta + consumer/test impact inventory.
2. **LLUP-10** — implement the single authoritative pin identity mechanism after LLUP-00 confirms owners.
3. **MRES-00** — in parallel, write the bounded residency state/invariant contract against the existing lifecycle owner; no runtime implementation yet.
4. **LLUP-20** — migrate the backend to exact `v0.3.0` once the delta inventory is frozen.
5. **LLUP-30 + LLUP-40** — run correctness and Android build/package lanes in parallel on the same exact candidate.
6. Continue to physical A/B and promotion only after the deterministic candidate is ready.
