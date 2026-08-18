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

The current pinned llama.cpp revision remains the Harness 0.5 baseline unless a correctness/security blocker justifies a controlled pin change. LLRT-0 may qualify a newer upstream candidate in parallel, but promotion must be an explicit decision because Q35 tuning, memory calibration, OMBRA quality evidence and release evidence are backend/runtime-identity bound.

If the pin is promoted before a release claim, affected physical/performance/quality evidence is rerun on the promoted revision. If promotion is deferred, the current revision is frozen through the release and the candidate becomes post-release work.

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
| LLRT-0 | P1 | READY | Qualify a newer llama.cpp candidate against the pinned baseline and make an explicit promote-vs-defer decision | Current green `dev` |
| LLRT-1 | P1 | PLANNED | Expose backend/device capabilities and requested/effective execution facts without leaking native handles | LLRT-0 decision; RA-7/RA-9 ownership |
| LLRT-2 | P1 | PLANNED | Reuse the already-tokenized native prompt plan so generation does not tokenize the same rendered prompt twice | Existing prompt-planning boundary |
| LLRT-3 | P1 | PLANNED | Add bounded CPU-side performance deltas for threads, batch threads, batch/ubatch and sustained thermal behavior without exploding the Q35 matrix | Frozen release pin; Q35-6 measurement owner |
| LLRT-4 | P1/P2 | PLANNED | Prove or reject safe Qwen3.5 recurrent/prefix/session state reuse on an exact backend revision; keep production reuse disabled until proven | LLRT-0; backend conformance; Qwen3.5 state semantics |
| LLRT-5 | P2 | PLANNED | Adopt newer load-mode semantics and tri-state Flash Attention only after the promoted pin exposes them and evidence shows a benefit | LLRT-0 promotion; LLRT-1 |
| LLRT-6 | P2 | PLANNED | Evaluate K/V cache data-type policy for memory/context benefit with correctness and quality checks | LLRT-0 promotion; MEM evidence; Q35 validation |
| LLRT-7 | P2 | PLANNED | Package and discover Adreno OpenCL as an experimental backend without changing the CPU release default | CPU release evidence stable; LLRT-1 |
| LLRT-8 | P2 | PLANNED | Integrate bounded OpenCL compiled-kernel cache ownership/cleanup for warm startup | LLRT-7 |
| LLRT-9 | P2 | PLANNED | Add multi-sequence/batched execution only for evaluation throughput; production single-decode policy stays unchanged | EVAL runner maturity; LLRT-0 promotion; backend correctness proof |
| LLRT-10 | P2 | PLANNED | Evolve RA-8 into a deterministic evidence-driven execution planner using reviewed measured profiles, not online self-tuning | RA-7/RA-8/RA-9; Q35/MEM measured evidence |
| LLRT-11 | P2 | PLANNED | Extend benchmark/evidence identity with backend/device/load/cache/reuse facts required to compare execution plans safely | LLRT-1; RA-9 |
| LLRT-12 | P3 | DEFERRED | Evaluate Hexagon/HTP as an experimental backend with explicit hardware/toolchain support boundaries | CPU/OpenCL evidence stable; LLRT-1 |

## LLRT-0 — upstream qualification gate

A pin update is a compatibility change, not routine dependency refresh. Qualification must compare the current revision and one explicit candidate revision for:

- host-native and Android build compatibility;
- JNI/API deltas and deprecated/changed parameters;
- packaged shared-library set and loader behavior;
- Qwen3.5 0.8B/2B load, prompt planning, structured output, reasoning, cancellation and cleanup;
- memory behavior and benchmark identity;
- relevant upstream Android/ARM/OpenCL fixes even when those backends remain disabled in release builds.

The output is one explicit decision: `PROMOTE` or `DEFER`. No `latest`/floating pin is introduced.

## CPU efficiency before hardware acceleration

### Native prompt-plan reuse

Prompt planning already renders and tokenizes the prompt to establish exact token count. Generation should be able to consume that prepared token sequence or an opaque native plan handle rather than round-tripping through another tokenization pass.

The plan/handle must be identity-bound to model, chat-template and backend revision, bounded in lifetime, explicitly released and invalidated by incompatible changes.

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
