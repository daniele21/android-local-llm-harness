# llama.cpp runtime efficiency and hardware execution target

Status: active
Document type: target-specification
Owner: llama-cpp-runtime
Canonical scope: target.llama-cpp-runtime-optimization
Read when: changing the llama.cpp upstream pin, prompt/context reuse, backend device execution, native performance knobs or hardware acceleration policy
Last reviewed: 2026-08-19

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

The pinned revision `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3` remains the Harness 0.5 baseline. LLRT-0 qualified candidate `60addddf3c567c43ec3caf70fc953fba3572d96f` and selected `DEFER`: adopting it would require an explicit model-load API migration and fresh affected evidence, so it is not promoted into the active release line.

A future pin candidate is qualified by exact SHA. Promotion remains an explicit decision because Q35 tuning, memory calibration, OMBRA quality evidence and release evidence are backend/runtime-identity bound. If a later candidate is promoted before a release claim, affected physical/performance/quality evidence is rerun on the promoted revision.

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
| LLRT-0 | P1 | DONE | Exact-SHA qualification harness integrated; candidate `60addddf...` classified `DEFER` and Harness 0.5 pin remains `aedb2a5e...` | Current green `dev` |
| LLRT-1 | P1 | IN PROGRESS | Backend/device capability inventory and requested load facts are integrated; authoritative effective placement remains explicitly unavailable on the current pin rather than inferred | LLRT-0; RA-7/RA-9 ownership |
| LLRT-2 | P1 | DONE | Consume the most recently prepared exact prompt tokens once so generation avoids duplicate tokenization while retaining the existing fallback | Existing prompt-planning boundary |
| LLRT-3 | P1 | IN PROGRESS | Bounded CPU delta runner and sustained warm/thermal evidence tooling are integrated; representative physical Android measurements remain the acceptance gate | Frozen release pin; Q35-6 measurement owner |
| LLRT-4 | P1/P2 | IN PROGRESS | Recurrent/session state correctness probe is integrated; exact Qwen3.5 0.8B/2B physical evidence is still required before any production reuse capability can change | LLRT-0; backend conformance; Qwen3.5 state semantics |
| LLRT-5 | P2 | PLANNED | Migrate newer load-mode semantics and tri-state Flash Attention on a post-0.5 candidate, preserving old load semantics before evaluating `AUTO` | Newer promoted pin; LLRT-1 |
| LLRT-6 | P2 | PLANNED | Evaluate K/V cache data-type policy for memory/context benefit with correctness and quality checks | Newer promoted pin; MEM evidence; Q35 validation |
| LLRT-7 | P2 | PLANNED | Package and discover Adreno OpenCL as an experimental backend without changing the CPU release default | CPU release evidence stable; LLRT-1 |
| LLRT-8 | P2 | PLANNED | Integrate bounded OpenCL compiled-kernel cache ownership/cleanup for warm startup | LLRT-7 |
| LLRT-9 | P2 | PLANNED | Add multi-sequence/batched execution only for evaluation throughput; production single-decode policy stays unchanged | EVAL runner maturity; newer promoted pin; backend correctness proof |
| LLRT-10 | P2 | PLANNED | Evolve RA-8 into a deterministic evidence-driven execution planner using reviewed measured profiles, not online self-tuning | RA-7/RA-8/RA-9; Q35/MEM measured evidence |
| LLRT-11 | P2 | DONE | Backend execution evidence is fingerprinted from the materialized context, propagated into run telemetry and persisted so material backend/load/cache/reuse changes invalidate benchmark comparability | LLRT-1; RA-9 |
| LLRT-12 | P3 | DEFERRED | Evaluate Hexagon/HTP as an experimental backend with explicit hardware/toolchain support boundaries | CPU/OpenCL evidence stable; LLRT-1 |

### Integrated P1 evidence boundary — 2026-08-19

LLRT-1A, LLRT-3A and LLRT-4A are integrated foundations rather than completed physical claims. LLRT-1A records the registered ggml device inventory and requested load facts while keeping effective placement unavailable when the pinned API cannot prove it. LLRT-3A provides the bounded one-factor CPU experiment lane. LLRT-4A provides the recurrent-state correctness probe while production recurrent/prefix reuse remains disabled.

LLRT-11 closes the comparability gap before physical tuning: after a runtime context is materialized, the backend emits a privacy-safe SHA-256 fingerprint over material execution inputs. The runtime records the backend ID/revision, fingerprint and explicit placement availability with the generation run; Room schema v9 preserves those fields across process restarts. The fingerprint covers the pinned backend revision, profile/context and CPU/batch knobs, requested GPU layers and mmap/mlock state, Flash Attention/KV-cache settings, stable registered-device inventory, prepared-prompt reuse mode and recurrent-state reuse mode. Effective placement remains `UNAVAILABLE` on the current pin rather than being guessed.

The next LLRT-3 acceptance step is therefore physical Android evidence against the exact curated Qwen3.5 artifacts. No runtime profile is promoted from CI, desktop or emulator measurements.

## LLRT-0 — upstream qualification gate

A pin update is a compatibility change, not routine dependency refresh. Qualification compares the current revision and one explicit candidate revision for host-native/Android build compatibility, JNI/API deltas, packaged libraries, Qwen3.5 execution behavior, memory/evidence identity and relevant Android/ARM backend changes.

The reusable qualification runner requires an exact 40-character SHA, never `latest`. Host-native and Android lanes execute independently in parallel. Each lane restores the repository gitlink and verifies the production pin even after failure or cancellation; a superseded CI run was used to verify this cleanup path.

### 2026-08-19 candidate decision

Candidate `60addddf3c567c43ec3caf70fc953fba3572d96f` is **DEFERRED for Harness 0.5**.

Evidence from qualification:

- the upstream penalties sampler changed from four arguments to a five-argument form requiring `n_vocab`; the adapter now supports both signatures at compile time while preserving repeat/frequency/presence semantics and failing closed on an unknown signature;
- host-native qualification passes after making the synthetic sampler fixture provide an explicit vocabulary size instead of relying on a null vocabulary;
- Android qualification reaches the JNI build and fails because the candidate removed `llama_model_params.use_mmap/use_mlock` in favor of `load_mode`;
- the candidate submodule is restored to the pinned gitlink after both normal execution and cancellation.

The load API change is intentionally not hidden inside LLRT-0. Mapping the legacy booleans, then separately evaluating `AUTO`, is LLRT-5 work because load policy affects execution identity and release evidence. The Harness 0.5 production pin therefore remains unchanged.

## CPU efficiency before hardware acceleration

### Native prompt-plan reuse

LLRT-2 is integrated. Prompt planning still renders and tokenizes the exact prompt for context sizing, but the llama.cpp model record retains at most one ephemeral prepared prompt/token sequence. Generation consumes it once only on an exact prompt match; a miss or mismatch discards stale state and falls back to the original tokenizer path.

This optimization stays inside `backends/llama-cpp`, exposes no native handles/tokens through backend-neutral contracts, persists no prompt content and does not change context/recurrent state. Model unload destroys the cache.

### Recurrent and prefix-state reuse

The current Qwen3.5 plan intentionally disables prefix/session reuse because Qwen3.5 uses hybrid/recurrent state. LLRT-4 therefore begins as a correctness experiment, not an optimization implementation.

Qualification must cover at least:

- exact token-equivalent output against clean-context execution;
- append-only conversational turns;
- divergent-prefix rollback/removal;
- cancellation during prefill/decode;
- model/context close and switch;
- memory pressure and warm-idle unload;
- structured output and reasoning modes;
- repeated cycles with no residual state contamination.

If any required state operation is unsupported or ambiguous, the capability stays false. Enabling it requires a new Qwen3.5 capability revision and fresh device/quality evidence rather than silently reopening Q35-5.

### CPU tuning discipline

Do not create a full Cartesian product of every llama.cpp knob. Keep the existing Q35-6 matrix as the baseline owner, then test only short-listed one-factor or paired deltas where profiling shows a plausible benefit.

Important dimensions include generation threads, batch/prefill threads, `n_batch`, `n_ubatch`, context tier and sustained thermal behavior. Peak token/s alone is not sufficient to select a mobile default.

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
- effective load mode and offloaded layers;
- effective Flash Attention and K/V cache type;
- context, batch, ubatch, generation threads and batch threads;
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
