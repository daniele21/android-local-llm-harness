# Qwen3.5 runtime qualification wave

Status: active
Document type: workstream-state
Owner: qwen35
Canonical scope: workstream.q35-runtime-qualification
Read when: coordinating LLRT-3C/Q35-6 measured-profile acceptance, lifecycle/memory evidence and the remaining representative-device gates
Last reviewed: 2026-08-25

Canonical Qwen3.5 product status remains in [`../qwen35/current-state.md`](../qwen35/current-state.md), while the llama.cpp ledger remains in [`../llama-cpp-runtime-optimization-plan.md`](../llama-cpp-runtime-optimization-plan.md). This file owns only temporary execution sequencing and dependency state for the bounded Q35-6 qualification wave.

## Goal

Close the remaining CPU-side Q35-6 acceptance gap without weakening existing correctness or evidence gates:

```text
candidate CPU profiles
        |
        +--> reviewed MEASURED acceptance contract
        |
        +--> lifecycle + memory physical evidence
        |
        +--> broader representative benchmark coverage
                        |
                        v
                 explicit review
                        |
             CANDIDATE or MEASURED
```

No slice in this workstream may automatically promote a profile, generalize one-device findings, change KV-cache defaults, broaden native-batching correctness, or claim OpenCL support from the Samsung Xclipse device.

## Invariants

- `dev` is the base/target and every software slice starts from an exact green `dev` identity.
- Production llama.cpp remains pinned to `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3`.
- Curated Qwen3.5 0.8B/2B Q4_K_M model SHA-256 identities remain fail-closed.
- Same-device physical suites are serialized and thermal-gated; independent software slices and different devices may run concurrently.
- `eligibleForProfileSelection` is evidence completeness, not authorization to mark a profile `MEASURED`.
- `MEASURED` promotion requires explicit reviewed benchmark provenance plus lifecycle, memory and representative-device acceptance.
- LLRT-6 stays `KEEP DEFAULTS`; 2B `q4_0/q4_0 + FA` remains research-only unless a separate quality-backed decision reopens it.
- LLRT-9C stays device/profile-scoped: 0.8B native batching rejected, 2B widths 2/3 positive, width 4 unqualified.
- LLRT-7C remains a separate Adreno representative-device lane; the current Xclipse phone cannot qualify it.

## Execution DAG

| ID | State | Depends on | Owns / writes | Can run with | Acceptance |
| --- | --- | --- | --- | --- | --- |
| QRT-00 | DONE | — | wave decomposition only | — | Write ownership and physical serialization boundaries are explicit. |
| QRT-10 | ACTIVE | QRT-00 | `models:model-profile` acceptance contract + focused tests | QRT-20, QRT-30 | Promotion remains explicit, exact-identity/provenance-bound and fail-closed when benchmark/lifecycle/memory/representative gates are incomplete. |
| QRT-20 | ACTIVE | QRT-00 | `apps:device-test-runner`, lifecycle evidence runner, regression guard | QRT-10, QRT-30 | Existing generation/unload/cancellation/PSS-cycle evidence is reused; LOW_MEMORY active cancellation/release and 0.8B <-> 2B switching are added; exact model/backend/Harness identity and thermal/cleanliness gates are recorded. |
| QRT-30 | ACTIVE | QRT-00 | this workstream only | QRT-10, QRT-20 | Dependency state stays current without duplicating canonical LLRT/Q35 status. |
| QRT-40 | BLOCKED | QRT-20 integrated | physical Samsung lifecycle/memory evidence | QRT-50 on another device | Both curated tiers pass the canonical lifecycle suite on one exact Harness/backend/device identity; evidence manifest is hashed and reviewed. |
| QRT-50 | BLOCKED | QRT-10 integrated | broader CPU benchmark evidence on representative devices | QRT-40 | Required candidate configurations have complete TTFT/prefill/decode/PSS/thermal evidence with exact comparable identities; no one-device result is generalized. |
| QRT-60 | BLOCKED | QRT-40, QRT-50 | explicit profile review + durable Q35/LLRT docs | — | Review either keeps `CANDIDATE` or promotes a provenance-bound `MEASURED` profile; no implicit/default promotion exists. |
| QRT-70 | BLOCKED | QRT-60 | final lifecycle/docs cleanup | LLRT-7C on representative Adreno | Q35-6 CPU-side acceptance criteria are transferred to durable owners and this temporary workstream is removed. |

## Parallel execution policy

`QRT-10`, `QRT-20` and `QRT-30` are intentionally independent. `QRT-10` owns only the model-profile promotion contract; `QRT-20` owns only physical lifecycle instrumentation/evidence tooling; `QRT-30` owns only temporary sequencing. They must not edit the same files.

After the software slices are integrated, `QRT-40` and representative-device `QRT-50` may run in parallel only when they use different physical devices. On the same phone, all physical performance/lifecycle suites are serialized to avoid thermal and residency contamination.

LLRT-7C is not on the critical path for CPU-side Q35-6 acceptance and should proceed independently only when an eligible Adreno 750/830 device is available. LLRT-8 remains downstream of representative LLRT-7 evidence. LLRT-10 remains downstream of reviewed `MEASURED` profiles.

## Current branches / merge order

1. `feat/q35-measured-profile-acceptance` — QRT-10; independent, may merge first.
2. `feat/q35-lifecycle-memory-evidence` — QRT-20; independent, may merge first.
3. `docs/q35-runtime-qualification-wave` — QRT-30; documentation-only coordination.
4. Rebase/refresh remaining branches from `dev` after each merge only when required by their base policy; do not alter physical evidence identities after a wave has started.
5. Physical QRT-40 starts only from a single clean merged `dev` identity containing QRT-10 and QRT-20.

## Physical acceptance boundary

The canonical same-device lifecycle wave must cover, for both curated tiers where applicable:

- load -> generate -> close session -> unload;
- cooperative cancellation after streaming starts;
- repeated load/generate/unload with bounded PSS growth;
- active-generation `LOW_MEMORY` cancellation and full resource release;
- 0.8B -> 2B -> 0.8B model switching with no stale residency;
- exact Harness commit, llama.cpp revision, model digests, device identity and thermal-start gate;
- append-only/log-backed evidence plus a SHA-256 manifest;
- no automatic profile promotion.

Performance/quality tuning remains owned by the existing Qwen3.5/LLRT runners; this wave does not redefine benchmark thresholds or reuse pre-normalization stale evidence.

## Exit conditions

Q35-6 may leave `IN PROGRESS` only when:

1. the promotion contract is integrated and fail-closed;
2. canonical lifecycle/memory evidence passes and is reviewed;
3. broader representative CPU evidence satisfies the chosen profile's acceptance coverage;
4. TTFT, prefill/decode throughput, peak PSS and thermal evidence are available for the reviewed configuration;
5. cancellation, model switching, memory pressure and idle unload acceptance pass;
6. the explicit review records either `KEEP_CANDIDATE` or `PROMOTE_MEASURED` with exact evidence provenance;
7. durable Q35/LLRT state is updated without claiming OpenCL/generalized batching evidence not actually obtained.

## Durable destinations on completion

Transfer final decisions to:

- [`../qwen35/current-state.md`](../qwen35/current-state.md) for Q35-6 state and evidence-backed blockers/decisions;
- [`../qwen35/workstreams/runtime-tuning.md`](../qwen35/workstreams/runtime-tuning.md) for durable tuning acceptance semantics;
- [`../llama-cpp-runtime-optimization-plan.md`](../llama-cpp-runtime-optimization-plan.md) for LLRT-3/5/7/9/10 state;
- memory-management documentation only if lifecycle acceptance changes durable memory policy.

When QRT-70 completes and durable knowledge is transferred, remove this temporary workstream by default.
