# llama.cpp runtime efficiency and hardware execution target

Status: active
Document type: target-specification
Owner: llama-cpp-runtime
Canonical scope: target.llama-cpp-runtime-optimization
Read when: changing the llama.cpp upstream pin, prompt/context reuse, backend device execution, native performance knobs or hardware acceleration policy
Last reviewed: 2026-08-21

## Purpose

This plan defines how the Harness should extract more efficiency from `llama.cpp` without creating a second runtime, bypassing backend-neutral contracts or invalidating the current Qwen3.5, memory, observability and release evidence owners.

The current product remains CPU-first, one loaded model and one active decode by default. Hardware acceleration, recurrent-state reuse and broader execution policy are evidence-gated follow-on capabilities, not reasons to destabilize the Harness 0.5 release line.

## Ownership and non-goals

- `backends/llama-cpp` owns native mechanism: upstream adaptation, model/context parameters, backend/device discovery, prompt token reuse and backend-specific state handling.
- `core/runtime-core` owns runtime lifecycle, admission, scheduling and execution-policy application; it must not depend on llama.cpp types.
- `docs/qwen35` owns Qwen3.5-specific support, tuning and certification decisions. Generic llama.cpp APIs do not automatically make a Qwen3.5 optimization safe.
- `docs/memory-management` owns memory budgeting/admission and consumes measured backend costs rather than deriving them from theoretical formulas.
- `observability` owns privacy-safe measurement contracts and benchmark history.
- `evaluation` owns dataset execution and may consume a throughput-oriented backend mode without changing production concurrency policy.
- `reference-architecture-hardening` owns device-policy purity, execution identity and backend conformance.

This plan does not introduce simultaneous production decodes, arbitrary model support, speculative decoding, cloud fallback, a second model store or runtime self-tuning that mutates policy from unreviewed measurements.

## Priority relative to active repository plans

Repository operational priority remains owned by [`current-state.md`](current-state.md). This workstream must not displace the current OMBRA and physical-evidence gates.

| Priority | Repository relation | llama.cpp work allowed |
| --- | --- | --- |
| P0 | OMB-6B/OMB-8, Q35-6/Q35-7, MEM-7/MEM-8, SR-6 and Harness 0.5 release evidence | No optimization is allowed to invalidate or delay the frozen CPU release baseline. Only correctness fixes may preempt this lane. |
| P1 | Parallel hardening after disjoint ownership is confirmed; RA-4/5/7/9/10 and model-evaluation work continue | Upstream qualification, capability/telemetry exposure, prompt-token reuse and bounded CPU-side measurements that preserve existing semantics. |
| P2 | After the CPU path is evidence-stable, or on an explicitly non-release experimental lane | Adreno OpenCL, OpenCL kernel cache, KV-cache type experiments, multi-sequence evaluation and deterministic device-plan evolution. |
| P3 | Research only after CPU/OpenCL baselines are understood | Hexagon/HTP and broader heterogeneous execution experiments. |

### Release-baseline rule

The pinned revision `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3` remains the Harness 0.5 baseline. Candidate `60addddf3c567c43ec3caf70fc953fba3572d96f` remains **DEFERRED for Harness 0.5**. LLRT-5 has since made the legacy load policy candidate-compatible and exact candidate host-native plus Android qualification succeeds without moving the production pin, but promotion is still a separate decision because affected physical/performance/quality evidence is backend-identity bound.

A future pin candidate is qualified by exact SHA. If a later candidate is promoted before a release claim, affected Q35 tuning, memory calibration, OMBRA quality evidence and release evidence are rerun on the promoted revision.

## Target execution architecture

```text
Device / model / workload facts
          |
          v
backend-neutral execution policy
          |
          v
LlamaCppInferenceBackend
  |        |         |
  |        |         +-- capability + effective-plan reporting
  |        +------------ prompt/context state adapter
  +--------------------- device/backend loader
          |
          v
llama.cpp / ggml
  | CPU
  | Adreno OpenCL       [P2 experimental]
  + Hexagon HTP         [P3 experimental]
```

The backend reports what is available and what actually ran. It does not choose product policy based on ad-hoc JNI heuristics.

## Workstream ledger

Status vocabulary: `PLANNED`, `READY`, `IN PROGRESS`, `BLOCKED`, `DONE`, `DEFERRED`.

| ID | Priority | State | Outcome | Depends on |
| --- | --- | --- | --- | --- |
| LLRT-0 | P1 | DONE | Exact-SHA qualification harness integrated; candidate `60addddf...` remains deferred for Harness 0.5 and production pin remains `aedb2a5e...` | Current green `dev` |
| LLRT-1 | P1 | IN PROGRESS | LLRT-1A capability inventory/requested load facts are integrated; LLRT-1B authoritative effective placement remains blocked on a newer API/pin rather than inferred | LLRT-0; RA-7/RA-9 ownership; future pin |
| LLRT-2 | P1 | DONE | Consume the most recently prepared exact prompt tokens once so generation avoids duplicate tokenization while retaining the existing fallback | Existing prompt-planning boundary |
| LLRT-3 | P1 | IN PROGRESS | Bounded 2B and 0.8B CPU screening plus focused realistic 2B prefill validation are complete; broader/representative Q35 validation still gates `MEASURED` profile promotion | Frozen release pin; Q35-6 measurement owner |
| LLRT-4 | P1/P2 | DONE | Exact-artifact 0.8B and 2B recurrent-state native correctness probes completed with explicit `KEEP_DISABLED`: supported restore paths are exact, but partial rollback is unsupported | LLRT-0; backend conformance; Qwen3.5 state semantics |
| LLRT-5 | P2 | IN PROGRESS | Candidate load-mode compatibility/qualification, materialized load-mode execution identity and Flash Attention tri-state mechanism are integrated; `AUTO` performance evaluation and any future pin-promotion decision remain evidence-gated | LLRT-0; LLRT-1A |
| LLRT-6 | P2 | PLANNED | Evaluate K/V cache data-type policy for memory/context benefit with correctness and quality checks | Evidence checkpoint; MEM evidence; Q35 validation |
| LLRT-7 | P2 | PLANNED | Package and discover Adreno OpenCL as an experimental backend without changing the CPU release default | Evidence checkpoint; LLRT-1 |
| LLRT-8 | P2 | PLANNED | Integrate bounded OpenCL compiled-kernel cache ownership/cleanup for warm startup | LLRT-7 |
| LLRT-9 | P2 | PLANNED | Add multi-sequence/batched execution only for evaluation throughput; production single-decode policy stays unchanged | EVAL runner maturity; backend correctness proof |
| LLRT-10 | P2 | PLANNED | Evolve RA-8 into a deterministic evidence-driven execution planner using reviewed measured profiles, not online self-tuning | RA-7/RA-8/RA-9; Q35/MEM measured evidence |
| LLRT-11 | P2 | DONE | Backend execution evidence is fingerprinted from the materialized context, propagated into run telemetry and persisted so material backend/load/cache/reuse changes invalidate benchmark comparability | LLRT-1; RA-9 |
| LLRT-12 | P3 | DEFERRED | Evaluate Hexagon/HTP as an experimental backend with explicit hardware/toolchain support boundaries | CPU/OpenCL evidence stable; LLRT-1 |

## Current execution wave — 2026-08-21

The top-level states remain conservative where broader product certification is still open. The implementation/evidence boundary is tracked more precisely here.

| Slice | State | Current boundary / next gate |
| --- | --- | --- |
| LLRT-1A | DONE | Registered ggml device inventory plus requested load facts integrated; unavailable effective placement remains explicit. |
| LLRT-1B | BLOCKED | Authoritative effective placement needs a future llama.cpp API/pin that can prove it; do not infer it on the Harness 0.5 pin. |
| LLRT-3A | DONE | Bounded CPU runner, macOS Bash 3.2 compatibility, instrumentation evidence stream, thermal-start gate, resumability and schema-v4 identity are integrated. |
| LLRT-3B-2B | DONE | Bounded Qwen3.5 2B screening complete; focused 283/553/1094-token prefill validation rejects the bounded `t4/bt2/b128/ub64` candidate for production. |
| LLRT-3B-0.8B | DONE | Bounded Qwen3.5 0.8B screening complete; `t2/bt4/b128/ub64` is the bounded priority candidate, not a `MEASURED` default. |
| LLRT-3C | BLOCKED | Promote no `MEASURED` profile until broader/representative Q35 validation and lifecycle/memory gates are reviewed. |
| LLRT-4A | DONE | Recurrent/session-state correctness probe integrated; production reuse remains disabled. |
| LLRT-4B | DONE | Exact curated 0.8B and 2B physical native correctness evidence completed on Samsung SM-A566B. |
| LLRT-4C | DONE | Verdict `KEEP_DISABLED` for both tiers because partial rollback is unsupported even though all supported restore-equivalence checks pass with `maxDelta=0`. |
| LLRT-5A | DONE | Legacy mmap/mlock combinations map to candidate `load_mode` and exact candidate host-native + Android qualification succeeds without a release-pin move. |
| LLRT-5B | DONE | Materialized load mode `NONE/MMAP/MLOCK/MMAP_MLOCK` is part of backend execution identity while legacy mmap/mlock facts remain for comparability. |
| LLRT-5C | DONE | Flash Attention mechanism carries explicit `AUTO=-1`, `DISABLED=0`, `ENABLED=1`; product profile `false/true` still maps to disabled/enabled and AUTO is not selected automatically. |
| LLRT-5D | BLOCKED | Evaluate `AUTO`/enabled performance and decide any future candidate-pin promotion only with physical evidence under a new execution identity. |

### LLRT-3 physical CPU evidence snapshot

All 2026-08-21 follow-up evidence uses production llama.cpp `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3`, Harness commit `b35d303f9f019e6304a5e628d20fefc5b944765f`, Samsung `SM-A566B`, Android 16 / SDK 36 / arm64-v8a, context 2048, 64 output tokens and thinking disabled.

For Qwen3.5 0.8B Q4_K_M SHA-256 `bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517`, all four bounded cases completed with 1 cold + 5 warm generations and were evidence-eligible. `t2/bt4/b128/ub64` is the bounded priority candidate: warm total latency improved from roughly 70.0 s baseline to 65.9 s while thermal status remained 0. This is search-space evidence only, not a measured default.

For Qwen3.5 2B Q4_K_M SHA-256 `aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223`, the earlier short 19-token bounded run had identified `t4/bt2/b128/ub64` as a memory-oriented candidate. Focused deterministic prefill validation then compared it with baseline `t4/bt4/b128/ub64` at 283, 553 and 1094 actual input tokens. Warm median baseline versus candidate total latency was approximately 313.7 vs 330.0 s, 465.3 vs 505.4 s and 754.3 vs 862.6 s respectively. At 1094 tokens the candidate also increased median prefill from about 619.6 s to 721.3 s. The candidate is therefore **REJECTED** for production; `t4/bt4/b128/ub64` remains the current 2B CPU candidate pending broader certification.

The ~10-minute 1094-token baseline prefill is a device/configuration-specific observation, not a universal Qwen3.5 2B claim. It is nevertheless a strong reason to preserve CPU measurements as the baseline for later hardware-acceleration evaluation.

### LLRT-4 recurrent-state physical verdict

The exact 0.8B and 2B native probes both report:

```text
append-only-equivalence        PASS maxDelta=0
divergent-restore-equivalence  PASS maxDelta=0
clear-restore-equivalence      PASS maxDelta=0
repeated-restore-equivalence   PASS maxDelta=0
full-sequence-remove           SUPPORTED
full-remove-restore-equivalence PASS maxDelta=0
partial-rollback               UNSUPPORTED
LLRT4_NATIVE_VERDICT           KEEP_DISABLED
```

This is a successful negative qualification result. Supported state-save/restore operations are bit-equivalent under the probe, but the missing partial rollback is sufficient to keep Qwen3.5 recurrent/prefix reuse disabled for production. No additional optimization work is required to justify that release decision; lifecycle hardening remains useful for the runtime generally.

## Parallelization model

The 2026-08-21 evidence checkpoint is now satisfied. Future wider P2 work remains an explicit implementation decision rather than an automatic consequence of passing the checkpoint.

### Lane A — CPU evidence and candidate narrowing

1. preserve the completed bounded 0.8B and 2B evidence as immutable search-space evidence;
2. keep `t2/bt4/b128/ub64` as the bounded 0.8B priority candidate only;
3. reject 2B `t4/bt2/b128/ub64` and retain `t4/bt4/b128/ub64` as the current CPU candidate;
4. add broader context/product/representative-device evidence only where the Q35 acceptance owner requires it;
5. feed accepted results into Q35-RT-08 rather than auto-promoting from a runner.

### Lane B — recurrent-state correctness

LLRT-4 is closed with `KEEP_DISABLED` on both curated tiers. Recurrent/prefix reuse remains disabled. Runtime cancellation, model/context close and switch, memory pressure/warm-idle unload and structured/reasoning lifecycle tests remain general hardening work, not prerequisites for turning this optimization on.

### Lane C — post-0.5 candidate-pin compatibility

LLRT-5 preserves the frozen release pin while making the newer load-mode API mechanically compatible. The four legacy mmap/mlock combinations have explicit materialized load-mode identity. Flash Attention is represented as a fail-closed tri-state across Kotlin/JNI/native code, but current product defaults remain unchanged and `AUTO` remains experimental until measured.

### Physical-device serialization rule

Software preparation may run concurrently across disjoint ownership. Performance/thermal measurements on the same physical phone must not run concurrently and must not be interleaved without a comparable thermal-start gate. If two independent representative devices are available, evidence can run in parallel by device; otherwise physical execution is serialized while software development remains parallel.

### Evidence checkpoint before wider P2 work

Checkpoint facts as of 2026-08-21:

- bounded 0.8B evidence is complete;
- the 2B bounded candidate has realistic prefill evidence and an explicit **REJECT** decision;
- LLRT-4 has an explicit evidence-backed `KEEP_DISABLED` verdict for both curated tiers;
- LLRT-5 has exact candidate compatibility/qualification without changing the release baseline, plus materialized load-mode identity and tri-state Flash Attention mechanism.

**Checkpoint status: SATISFIED.** LLRT-6 K/V policy and LLRT-7 OpenCL discovery are now eligible to start in parallel if their native ownership is kept disjoint. LLRT-8 follows LLRT-7. LLRT-10 still waits for reviewed measured CPU/memory/hardware profiles. LLRT-9 remains an evaluation-owned side lane. Eligibility does not itself authorize or promote any P2 feature.

## Integrated P1 evidence boundary

LLRT-1A, LLRT-2, LLRT-3A, the bounded/focused LLRT-3 physical slices and LLRT-4 are integrated foundations. LLRT-1A records the registered ggml device inventory and requested load facts while keeping effective placement unavailable when the pinned API cannot prove it. LLRT-3 provides the bounded one-factor CPU experiment lane and focused prefill validation. LLRT-4 closes recurrent-state reuse with `KEEP_DISABLED` rather than fabricating support.

LLRT-11 closes the comparability gap before physical tuning: after a runtime context is materialized, the backend emits a privacy-safe SHA-256 fingerprint over material execution inputs. The runtime records the backend ID/revision, fingerprint and explicit placement availability with the generation run; Room schema v9 preserves those fields across process restarts. The fingerprint covers the pinned backend revision, profile/context and CPU/batch knobs, requested GPU layers, requested mmap/mlock state, **materialized load mode**, Flash Attention/KV-cache settings, stable registered-device inventory, prepared-prompt reuse mode and recurrent-state reuse mode. Effective placement remains `UNAVAILABLE` on the current pin rather than being guessed.

The physical lane uses Bash 3.2-compatible scripting, instrumentation status output, bounded generation timeouts, thermal-start gating and evidence schema **v4** with explicit output-token budget and prompt-digest identity. Exact-case execution/resume and output identity protect evidence from interrupted or cross-tier runs.

No runtime profile is promoted from CI, desktop/emulator or bounded single-device measurements.

## LLRT-0 and LLRT-5 — upstream qualification and compatibility

A pin update is a compatibility change, not routine dependency refresh. Qualification compares the current revision and one explicit candidate revision for host-native/Android build compatibility, JNI/API deltas, packaged libraries, Qwen3.5 execution behavior, memory/evidence identity and relevant Android/ARM backend changes.

The reusable qualification runner requires an exact 40-character SHA, never `latest`. Host-native and Android lanes execute independently in parallel. Each lane restores the repository gitlink and verifies the production pin even after failure or cancellation.

### Candidate history and current decision

Candidate `60addddf3c567c43ec3caf70fc953fba3572d96f` remains **DEFERRED for Harness 0.5**.

Initial qualification found two concrete API migrations:

- the penalties sampler changed from four arguments to a five-argument form requiring `n_vocab`; the adapter supports both signatures at compile time while preserving semantics and failing closed on an unknown signature;
- model load replaced `llama_model_params.use_mmap/use_mlock` with `load_mode`.

LLRT-5 subsequently added a compatibility adapter that preserves exactly the four legacy combinations `NONE`, `MMAP`, `MLOCK` and `MMAP_MLOCK`, without silently selecting candidate-only `AUTO` or `DIRECT_IO`. The recurrent-state probe was also made candidate-compatible. Exact candidate qualification then passed both host-native and Android lanes with the production pin restored afterward.

Execution evidence now includes the materialized load mode in addition to the legacy requested mmap/mlock dimensions. Flash Attention mechanism carries explicit `AUTO=-1`, `DISABLED=0` and `ENABLED=1`; current profile booleans still map to explicit disabled/enabled, so no product behavior changes and AUTO remains evidence-gated.

This removes the known mechanical migration blocker but does **not** promote the candidate pin or invalidate existing production-pin evidence.

## CPU efficiency before hardware acceleration

### Native prompt-plan reuse

LLRT-2 is integrated. Prompt planning still renders and tokenizes the exact prompt for context sizing, but the llama.cpp model record retains at most one ephemeral prepared prompt/token sequence. Generation consumes it once only on an exact prompt match; a miss or mismatch discards stale state and falls back to the original tokenizer path.

This optimization stays inside `backends/llama-cpp`, exposes no native handles/tokens through backend-neutral contracts, persists no prompt content and does not change context/recurrent state. Model unload destroys the cache.

### Recurrent and prefix-state reuse

Qwen3.5 uses hybrid/recurrent state, so generic context-state APIs are not sufficient proof of safe prefix/session reuse. LLRT-4 was intentionally a correctness experiment rather than an optimization implementation.

Exact physical probes on both curated tiers show exact supported restore equivalence but no partial rollback. The capability therefore stays false and production recurrent/prefix reuse remains disabled. Enabling it in a future workstream would require a new Qwen3.5 capability revision plus fresh lifecycle/device/quality evidence; the current release decision is closed.

### CPU tuning discipline

Do not create a full Cartesian product of every llama.cpp knob. Keep Q35-6 as the baseline owner, then test only short-listed one-factor or paired deltas where profiling shows a plausible benefit.

Important dimensions include generation threads, batch/prefill threads, `n_batch`, `n_ubatch`, context tier and sustained thermal behavior. Peak token/s alone is not sufficient to select a mobile default. The 2B realistic-prefill rejection is the concrete example: a short-prompt memory-oriented candidate regressed increasingly as prompt length grew.

## Hardware execution

### Adreno OpenCL

OpenCL is the first hardware-acceleration lane because upstream llama.cpp has an Android/Adreno backend and the repository already enables dynamic ggml backend loading. The first milestone is experimental packaging/discovery, not product-default offload.

The backend must expose device identity, supported features and effective offload; runtime policy stays conservative and backend-neutral. Unsupported devices fail closed to the already-supported CPU path only when policy explicitly selects CPU fallback; there is no silent change in execution identity.

OpenCL compiled-kernel cache must have an app-owned directory, bounded cleanup/retention and identity that includes relevant backend/device/driver inputs. Cache failure may reduce startup performance but must not corrupt inference.

### KV-cache data types

K/V cache quantization is treated as a memory/performance policy with possible quality/correctness consequences. `AUTO`, F16 and evidence-backed quantized types may be considered, but no type becomes a product default from a theoretical memory estimate alone.

### Hexagon/HTP

Hexagon remains research-only until CPU and OpenCL ownership, packaging, conformance and physical evidence are stable. Toolchain/vendor requirements and supported SoCs must be explicit; HTP support must never make the generic Android product claim broader than the measured device set.

## Evaluation-mode batching

Multi-sequence execution may improve dataset-evaluation throughput, but it is a separate execution mode. It must not alter the production `SingleDecodeScheduler` contract or imply support for simultaneous interactive generations.

Evaluation batching requires exact per-sequence result attribution, cancellation/timeout isolation, deterministic scoring identity and backend-specific correctness evidence, especially for accelerator backends.

## Deterministic device policy

The long-term target is not an online auto-tuner that changes behavior unpredictably. RA-8 remains the policy owner and consumes reviewed facts:

```text
DeviceCapabilities
+ model/artifact identity
+ workload tier
+ current resource/thermal state
+ measured profile registry
-> versioned ExecutionPlan
```

Identical inputs and policy version must yield the same plan. New measurements become inputs only after review/promotion to a measured profile.

## Required observability and evidence

Reuse existing metric/identity owners. Add only missing material dimensions, such as:

- requested and effective backend/device;
- effective/materialized load mode and offloaded layers;
- effective Flash Attention and K/V cache type;
- context, batch, ubatch, generation threads and batch threads;
- output-token budget when it affects the measured sustained workload;
- model load time, TTFT, prefill/decode duration and throughput;
- prompt tokens actually evaluated versus safely reused tokens when reuse exists;
- process PSS/available memory and thermal status from existing resource observation;
- exact llama.cpp revision, Harness revision and execution-policy/profile version.

Unavailable data remains unavailable rather than becoming zero. Prompts/generated text remain excluded from normal telemetry.

## Acceptance rules

- No task becomes `DONE` from desktop/emulator performance evidence when the claim is Android performance, memory or thermal behavior.
- A faster configuration is rejected if correctness, cancellation, cleanup, memory headroom or sustained performance regresses beyond the owning acceptance policy.
- Production CPU behavior stays the fallback baseline until a hardware backend has explicit supported-device scope, backend conformance and representative physical evidence.
- New cache/state owners define lifetime, maximum cardinality, invalidation, pressure behavior, cleanup and metrics before implementation.
- A pin/backend/policy change that materially affects release evidence requires new evidence under the new identity.

## Relationship to active plans

- [`qwen35/workstreams/runtime-tuning.md`](qwen35/workstreams/runtime-tuning.md) remains the owner of Qwen3.5 candidate/measured runtime profiles.
- [`memory-management/README.md`](memory-management/README.md) remains the owner of admission, memory cost profiles and memory certification.
- [`reference-architecture-hardening-plan.md`](reference-architecture-hardening-plan.md) remains the owner of RA-7/8/9/10 cross-cutting policy, identity and conformance boundaries.
- [`model-evaluation/README.md`](model-evaluation/README.md) remains the owner of evaluation execution semantics and comparison.
- [`benchmark-engine.md`](benchmark-engine.md) remains the owner of telemetry-derived runtime regressions.
- [`roadmap.md`](roadmap.md) owns repository-level capability sequencing; this file owns only the detailed llama.cpp optimization target.
