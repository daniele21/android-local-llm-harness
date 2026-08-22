# llama.cpp runtime efficiency and hardware execution target

Status: active
Document type: target-specification
Owner: llama-cpp-runtime
Canonical scope: target.llama-cpp-runtime-optimization
Read when: changing the llama.cpp upstream pin, prompt/context reuse, backend device execution, native performance knobs or hardware acceleration policy
Last reviewed: 2026-08-22

## Purpose

Define how the Harness extracts more efficiency from `llama.cpp` without bypassing backend-neutral contracts or invalidating Qwen3.5, memory, observability and release evidence.

The product remains CPU-first with one loaded model and one active decode by default. Hardware acceleration, cache changes and broader execution policy stay evidence-gated.

## Ownership and non-goals

- `backends/llama-cpp`: native mechanism, upstream adaptation, context/load parameters, device discovery and backend-specific state.
- `core/runtime-core`: lifecycle, admission, scheduling and execution-policy application; no llama.cpp types cross this boundary.
- `docs/qwen35`: Qwen3.5 support, tuning and certification decisions.
- `docs/memory-management`: memory budgeting, admission and measured cost profiles.
- `observability`: privacy-safe measurement and benchmark history.
- `evaluation`: dataset execution and evaluation-only throughput modes.
- `reference-architecture-hardening`: device-policy purity, execution identity and backend conformance.

Non-goals: simultaneous production decodes, arbitrary model support, speculative decoding, cloud fallback, a second model store or unreviewed online self-tuning.

## Priority and release baseline

Repository priority remains owned by [`current-state.md`](current-state.md). This lane must not invalidate the frozen Harness 0.5 CPU release baseline.

| Priority | llama.cpp work allowed |
| --- | --- |
| P0 | Correctness fixes required by active release/evidence work. |
| P1 | Qualification, capability/telemetry exposure, prompt reuse and bounded CPU measurement. |
| P2 | Evidence-gated OpenCL, KV-cache experiments, evaluation batching and deterministic planning. |
| P3 | Hexagon/HTP research after CPU/OpenCL evidence is stable. |

Production llama.cpp remains pinned to `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3`. Candidate `60addddf3c567c43ec3caf70fc953fba3572d96f` is **DEFERRED for Harness 0.5**. Mechanical compatibility does not authorize a pin move; a promoted pin requires fresh affected Q35, memory, OMBRA and release evidence.

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

The backend reports available and materialized execution facts. Product policy is not selected by ad-hoc JNI heuristics.

## Workstream ledger

Status vocabulary: `PLANNED`, `READY`, `IN PROGRESS`, `BLOCKED`, `DONE`, `DEFERRED`.

| ID | Priority | State | Outcome / next gate |
| --- | --- | --- | --- |
| LLRT-0 | P1 | DONE | Exact-SHA qualification harness integrated; production pin unchanged. |
| LLRT-1 | P1 | IN PROGRESS | Registered device inventory/requested load facts integrated; authoritative effective placement waits for a future API/pin. |
| LLRT-2 | P1 | DONE | Consume the most recently prepared exact prompt tokens once with the original tokenizer fallback preserved. |
| LLRT-3 | P1 | IN PROGRESS | Bounded 0.8B/2B CPU screening and focused 2B prefill evidence complete; broader Q35 evidence still gates `MEASURED` promotion. |
| LLRT-4 | P1/P2 | DONE | Recurrent-state probe closed `KEEP_DISABLED`: supported restores are exact, partial rollback is unsupported. |
| LLRT-5 | P2 | IN PROGRESS | Load-mode compatibility/materialized identity and Flash Attention tri-state integrated; AUTO/pin promotion remain evidence-gated. |
| LLRT-6 | P2 | IN PROGRESS | K/V materialization and fixed-seed physical-evidence tooling are integrated; curated device evidence is the next policy gate. |
| LLRT-7 | P2 | IN PROGRESS | Reproducible default-off OpenCL build/preflight/evidence tooling is integrated; representative exact-artifact device evidence remains required. |
| LLRT-8 | P2 | PLANNED | Bounded OpenCL compiled-kernel cache ownership and cleanup. |
| LLRT-9 | P2 | IN PROGRESS | Evaluation orchestration, exact-pin multi-sequence qualification, capacity planning, native multi-sequence decode, sampled-token acceptance normalization, backend-neutral batch SPI, runtime ownership/admission, llama.cpp/JNI bridging and the evaluation runtime adapter are integrated; refreshed deterministic and physical evidence remain. |
| LLRT-10 | P2 | PLANNED | Deterministic evidence-driven execution planner using reviewed measured profiles. |
| LLRT-11 | P2 | DONE | Material backend execution identity is fingerprinted, propagated and persisted. |
| LLRT-12 | P3 | DEFERRED | Hexagon/HTP evaluation after CPU/OpenCL evidence is stable. |

## Current execution wave

| Slice | State | Boundary / next gate |
| --- | --- | --- |
| LLRT-1A | DONE | Registered ggml device inventory plus requested load facts integrated. |
| LLRT-1B | BLOCKED | Effective placement must remain `UNAVAILABLE` until the pinned API can prove it. |
| LLRT-3A | DONE | Bounded CPU runner, thermal-start gate, resumability and schema-v4 evidence integrated. |
| LLRT-3B-2B | DONE | `t4/bt2/b128/ub64` rejected after realistic prefill validation; `t4/bt4/b128/ub64` remains the 2B CPU candidate. |
| LLRT-3B-0.8B | DONE | `t2/bt4/b128/ub64` remains the bounded 0.8B priority candidate, not a measured default. |
| LLRT-3C | BLOCKED | Broader Q35 validation and lifecycle/memory gates still block `MEASURED` promotion. |
| LLRT-4A/B/C | DONE | Exact 0.8B/2B physical probes support `KEEP_DISABLED` for recurrent/prefix reuse. |
| LLRT-5A/B/C | DONE | Candidate load-mode compatibility, materialized load-mode identity and FA tri-state are integrated. |
| LLRT-5D | BLOCKED | AUTO/FA performance and any future pin promotion require physical evidence. |
| LLRT-6A | DONE | Exact cache names fail closed and explicit K/V values materialize atomically into `type_k/type_v`; null preserves defaults. |
| LLRT-6B | DONE | Schema-v5 fixed-seed runner records K/V, FA, output digest, memory, latency and thermal evidence without changing release defaults. |
| LLRT-6C | READY | Run curated 0.8B/2B device evidence and either select an evidence-backed policy or keep defaults. |
| LLRT-7A/B | DONE | OpenCL remains default-off; representative Adreno 750/830 loader preflight is not a support claim. |
| LLRT-7C | IN PROGRESS | Reproducible OpenCL build, packaging checks and schema-v6 runner are ready; representative exact-artifact physical evidence is still required. |
| LLRT-9A | DONE | Evaluation-only bounded batch planning/execution with exact ordered attribution and a serial compatibility path is integrated; production concurrency is unchanged. |
| LLRT-9B1 | DONE | Exact pin qualifies default `n_seq_max=1`, explicit multi-sequence batch allocation, per-output logits and per-sequence cleanup APIs; this is API compatibility, not a runtime batching claim. |
| LLRT-9B2A | DONE | Evaluation-only aggregate/per-sequence context capacity is planned explicitly for multi-sequence contexts without mutating the production context path. |
| LLRT-9B2B1 | DONE | Backend-local native kernel owns independent `seq_id`, sampler/cancellation state, sequential per-sequence prefill, shared decode batches, exact ordered attribution and fail-closed cleanup; it is not exposed outside the backend yet. |
| LLRT-9B2B2a | DONE | Backend-neutral optional evaluation-batch SPI defines bounded 2..4 sequence contexts, ordered request attribution and cooperative per-case cancellation without broadening production `InferenceBackend`. |
| LLRT-9B2B2b | DONE | Runtime seam schedules one bounded evaluation batch as one background decode unit, reuses only the resident model, owns a dedicated evaluation context, admits aggregate context memory fail-closed, preserves ordered attribution/per-case cancellation and leaves ordinary `session.context` plus production `generate()` semantics unchanged. |
| LLRT-9B2B2c | DONE | The llama.cpp backend/JNI bridge reuses the already-resident model, owns a dedicated bounded multi-sequence context, shares cancellation identity with normal generation and fingerprints width plus aggregate/per-sequence context without widening production `generate()`. |
| LLRT-9B2B2d | DONE | `evaluation:runtime-adapter` composes `EvaluationBatchExecutionPort` onto the runtime-only batch client with isolated stateless sessions, exact ordered attribution, timeout/cancellation and serial one-case fallback; `evaluation:engine` remains backend-neutral. |
| LLRT-9C | BLOCKED | Software batching gates are complete; physical serial-vs-native-batch correctness/throughput/memory/thermal evidence now waits only for refreshed deterministic output/correctness evidence under the normalized sampler semantics. |

## Physical evidence snapshot

All current CPU evidence uses the production llama.cpp pin, Samsung `SM-A566B`, Android 16 / SDK 36 / arm64-v8a, context 2048, 64 output tokens and thinking disabled.

For Qwen3.5 0.8B Q4_K_M (`bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517`), `t2/bt4/b128/ub64` improved bounded warm total latency from about 70.0 s to 65.9 s with thermal status 0. This is search-space evidence only.

For Qwen3.5 2B Q4_K_M (`aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223`), realistic prefill at 283/553/1094 actual input tokens rejected `t4/bt2/b128/ub64`: warm total latency was about 330.0/505.4/862.6 s versus baseline 313.7/465.3/754.3 s. At 1094 tokens candidate prefill was about 721.3 s versus 619.6 s baseline.

LLRT-4 physical probes on both curated tiers show exact supported restore equivalence (`maxDelta=0`) but `partial-rollback UNSUPPORTED`; recurrent/prefix reuse therefore stays disabled.

Physical performance/thermal runs on the same phone are serialized and thermal-gated. Parallel software preparation is allowed; separate devices may run independent evidence lanes.

Sampler acceptance normalization changes generation state semantics for penalties and optional grammar constraints. Historical latency/memory search-space observations remain historical performance evidence, but deterministic output digests and correctness comparisons that depend on sampler history are stale until replayed under the normalized semantics.

## Evidence checkpoint

The P2 checkpoint is **SATISFIED**: bounded 0.8B evidence is complete, the 2B CPU candidate has an explicit reject decision, LLRT-4 has an evidence-backed `KEEP_DISABLED` verdict and LLRT-5 mechanical compatibility is integrated without moving the release pin.

LLRT-6 and LLRT-7 may therefore proceed experimentally with release defaults unchanged. LLRT-6 now waits on the curated KV-cache device matrix; LLRT-7 now waits on representative exact-artifact OpenCL device evidence. The LLRT-9 software path through the native llama.cpp/JNI bridge and backend-neutral evaluation runtime adapter is integrated without changing production concurrency policy. Before LLRT-9C, affected deterministic output/correctness evidence must be replayed under the normalized sampler semantics; then the physical serial-vs-native-batch comparison becomes the next gate. LLRT-8 follows representative LLRT-7 evidence; LLRT-10 still waits for reviewed measured CPU/memory/hardware profiles.

## Integrated execution identity

LLRT-11 fingerprints material execution inputs after context creation and persists backend revision, execution fingerprint and placement availability with the run. The fingerprint covers profile/context and CPU/batch knobs, requested GPU layers, mmap/mlock and materialized load mode, Flash Attention/KV-cache settings, registered-device inventory, prompt-reuse mode and recurrent-state mode.

Existing LLRT-3 evidence remains schema **v4**. LLRT-6 fixed-seed cache experiments use schema **v5** to add K/V cache type, Flash Attention, generation-seed identity and a privacy-safe output digest. LLRT-7 OpenCL experiments use schema **v6** to add the experimental-build flag, CPU-control versus requested-offload lane, requested GPU layers, backend target/library presence and explicit `effectivePlacement=UNAVAILABLE` while the pinned API cannot prove placement. Prompt and generated text are not persisted.

No runtime profile is promoted from CI, desktop/emulator or bounded single-device evidence.

## LLRT-0 / LLRT-5 upstream qualification

A pin update is a compatibility change, not a routine dependency refresh. Qualification is exact-SHA and checks host-native/Android build compatibility, JNI/API deltas, packaging, Qwen3.5 behavior and evidence identity. Each qualification restores and verifies the production pin after execution.

Candidate `60addddf3c567c43ec3caf70fc953fba3572d96f` required two known migrations: penalties sampler `n_vocab` compatibility and `llama_model_params.load_mode` replacing legacy mmap/mlock fields. Adapters preserve the four legacy load combinations and do not silently select candidate-only modes. Exact candidate host-native and Android qualification passes, but the pin remains deferred.

## CPU efficiency

LLRT-2 keeps at most one ephemeral prepared prompt/token sequence. Generation consumes it only on an exact prompt match; mismatch falls back to the original tokenizer path. No prompt content or native token handles cross backend-neutral contracts.

Qwen3.5 recurrent/prefix reuse remains disabled because LLRT-4 found no partial rollback despite exact supported restores.

CPU tuning stays bounded: test short-listed one-factor or paired deltas rather than Cartesian products. Evaluate context, generation threads, batch threads, `n_batch`, `n_ubatch`, latency, memory and sustained thermal behavior together; peak token/s alone is not a mobile default criterion.

## Hardware execution

### Adreno OpenCL

OpenCL is experimental and default-off. The reproducible lane requires explicit headers plus an AArch64 link-time `libOpenCL.so`, verifies that `libggml-opencl.so` is packaged and that the vendor loader is not redistributed, and can source the exact device loader locally for the build. Representative Adreno 750/830 eligibility is only a preflight condition, not a model/backend support claim.

The physical runner compares a `gpuLayers=0` CPU control against explicitly bounded requested-offload values on the same experimental build. The current pin cannot authoritatively prove effective layer placement, so schema v6 records `effectivePlacement=UNAVAILABLE`; requested GPU layers must not be reported as effective placement. Curated Qwen3.5 Q4_K_M artifacts still require correctness, memory, latency and thermal evidence before any product selection.

Any compiled-kernel cache introduced by LLRT-8 must be app-owned, bounded, invalidated by relevant backend/device/driver identity and safe to lose.

### KV-cache data types

Exact supported K/V cache names fail closed and explicit values materialize in the pinned llama.cpp context. Quantized V cache requires Flash Attention on this pin, so LLRT-6 separates K-only/FA-off comparisons from K+V/FA-on comparisons.

The LLRT-6 runner records fixed-seed output digest, memory, latency and thermal evidence. Output-digest drift is a review signal, not an automatic verdict. No cache type becomes default from theoretical memory estimates or CI alone.

### Hexagon/HTP

Hexagon remains research-only until CPU/OpenCL ownership, packaging, conformance and physical evidence are stable. Supported SoCs and vendor/toolchain requirements must be explicit.

## Evaluation-mode batching

LLRT-9A is an evaluation-only orchestration seam: it groups the immutable ordered sample set into bounded batches, requires exact per-case attribution and supplies a sequential compatibility executor over the existing single-case port. It does **not** imply concurrent decode or native batching and does not change production scheduling.

LLRT-9B1 proves only that the exact production pin exposes and links the primitives required by a multi-sequence path: the default context remains `n_seq_max=1`, explicit sequence IDs can be carried by `llama_batch`, logits are addressable per output and a complete sequence can be removed independently. With the pin's default `kv_unified=false`, increasing `n_seq_max` divides the configured context across sequences, so multi-sequence execution owns aggregate context capacity and memory explicitly rather than mutating the production context.

LLRT-9B2A establishes that capacity boundary. LLRT-9B2B1 implements the backend-local native generation kernel: prompts are assigned independent sequence IDs, prefill remains deliberately sequential per sequence, active generated tokens are decoded together in bounded shared batches, each sequence owns its sampler and cancellation flag, all logits from one shared decode are sampled before any sequence cleanup, and completion/cancellation removes only the relevant sequence. Decode, attribution, UTF-8 or cleanup failures fail the batch closed. This is a software mechanism only and makes no throughput claim.

LLRT-9B2B2a introduces an optional backend-neutral batch SPI rather than widening production `InferenceBackend`. It bounds evaluation contexts to 2..4 sequences, carries ordered per-request outputs/outcomes and preserves cooperative per-case cancellation. The SPI alone does not authorize execution: `RuntimeOrchestrator` remains the lifecycle/scheduling owner.

LLRT-9B2B2b integrates that runtime owner. One evaluation batch enters `SingleDecodeScheduler` as one background operation, reuses only the already-resident selected model and materializes a dedicated evaluation context rather than ordinary `session.context`. Each case uses an isolated stateless session, ordered attribution is validated fail-closed and per-case cancellation is delegated only after the batch is running. Aggregate memory admission evaluates `perSequenceContext × width`; when memory-aware admission is enabled, missing aggregate estimates fail closed. Thinking-enabled batches also fail closed for now because coarse batch output does not yet preserve the serial `GenerationGuard` and reasoning-stream parser semantics. Production scheduler capacity and ordinary `generate()` behavior are unchanged.

LLRT-9B2B2c adapts `LlamaCppInferenceBackend` through a dedicated evaluation JNI API to the integrated native kernel while preserving the flat production `createContext` ABI and fingerprinting batch width plus per-sequence and aggregate context capacity. LLRT-9B2B2d composes that runtime operation into `EvaluationBatchExecutionPort` through `evaluation:runtime-adapter`, preserving immutable sample order, isolated stateless sessions, timeout/cancellation and serial one-case fallback while keeping `evaluation:engine` backend-neutral. Neither slice changes `SingleDecodeScheduler` production policy.

Issue #370 is resolved in software by normalizing sampled-token acceptance at the shared generation-sampler boundary: one token sampled through `llama_sampler_sample()` reaches stateful sampler `accept` exactly once even while the current decode loops retain their compatibility accept, while explicitly injected tokens still reach sampler state. Because this correction can change penalty and grammar state evolution, deterministic output/correctness evidence collected under the previous semantics is stale and must be refreshed before LLRT-9C.

LLRT-9C then requires representative correctness, throughput, memory and thermal evidence before any evaluation execution policy selects the native path.

## Deterministic device policy

RA-8 remains the policy owner:

```text
DeviceCapabilities
+ model/artifact identity
+ workload tier
+ resource/thermal state
+ reviewed measured profiles
-> versioned ExecutionPlan
```

Identical inputs and policy version must yield the same plan. Measurements become policy inputs only after review/promotion.

## Required observability

Record material dimensions needed for comparability:

- requested/effective backend and device when provable;
- materialized load mode, offloaded layers, Flash Attention and K/V cache type;
- context, batch, ubatch, generation threads and batch threads;
- output-token budget for sustained workloads;
- model load, TTFT, prefill/decode duration and throughput;
- prompt tokens evaluated/reused where available;
- process PSS, available memory and thermal status;
- exact llama.cpp, Harness and execution-policy/profile versions.

Unavailable values remain unavailable rather than becoming zero. Prompt/generated text is excluded from normal telemetry.

## Acceptance rules

- Android performance, memory and thermal claims require physical-device evidence.
- Faster candidates are rejected when correctness, cleanup, cancellation, memory headroom or sustained performance regresses beyond the owning policy.
- CPU remains the production baseline until a hardware backend has explicit supported-device scope, conformance and representative evidence.
- New cache/state owners define lifetime, bounds, invalidation, pressure behavior, cleanup and metrics before implementation.
- Material pin/backend/policy changes invalidate affected release evidence and require new evidence under the new identity.

## Relationship to active plans

- [`qwen35/workstreams/runtime-tuning.md`](qwen35/workstreams/runtime-tuning.md): Qwen3.5 candidate/measured runtime profiles.
- [`memory-management/README.md`](memory-management/README.md): admission, memory cost profiles and memory certification.
- [`reference-architecture-hardening-plan.md`](reference-architecture-hardening-plan.md): RA-7/8/9/10 policy, identity and conformance.
- [`model-evaluation/README.md`](model-evaluation/README.md): evaluation execution semantics and comparison.
- [`benchmark-engine.md`](benchmark-engine.md): telemetry-derived runtime regressions.
- [`roadmap.md`](roadmap.md): repository-level capability sequencing.
