# Qwen3.5-only product roadmap

Status: active
Document type: roadmap
Owner: qwen35
Canonical scope: qwen35.roadmap
Read when: selecting the next Qwen3.5 milestone, checking dependencies or deciding whether a later capability can start
Last reviewed: 2026-08-09

This roadmap owns milestone order and exit gates. Task-level implementation belongs in the linked workstream specifications; current progress belongs in [`current-state.md`](current-state.md).

## Sequence

```text
Q35-0 Decision/plan               DONE
   |
Q35-1 Curated model baseline      DONE
   |
Q35-2 Compatibility gate          DONE
   |
Q35-3 Thinking + sampling         DONE
   |
Q35-4 Generation guard            DONE
   |
Q35-5 Runtime/context/cache       DONE
   |
Q35-6 Android tuning              IN PROGRESS
   |
Q35-7 Validation                  PLANNED
   |
Q35-8 Certification               PLANNED
```

## Q35-0 — Decision and planning

State: **DONE**

ADR 0011 owns the dense 0.8B/2B Qwen3.5-only product envelope and non-goals.

## Q35-1 — Curated model baseline

State: **DONE**

The executable product catalog is closed to seven reviewed Qwen3.5 dense 0.8B/2B releases. Consumer manual GGUF import and retired-family compatibility paths are removed; binding, persistence and inventory are catalog anchored; Models presents one reconciled lifecycle view; applicable repository, Android and package validation is green.

Owner: [`workstreams/curated-model-baseline.md`](workstreams/curated-model-baseline.md)

## Q35-2 — Model/backend compatibility

State: **DONE**

The exact Qwen3.5 0.8B Q4_K_M and 2B Q4_K_M reference artifacts are pinned by identity and trusted GGUF metadata. Both pass the final exact-artifact `load -> tokenize -> minimal generate` smoke against pinned llama.cpp revision `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3`.

Owner: [`workstreams/model-compatibility.md`](workstreams/model-compatibility.md)

## Q35-3 — Thinking, template and sampling

State: **DONE**

Neutral thinking intent maps to typed Jinja `enable_thinking`; no prompt soft-switch is injected. `minP`, `presencePenalty` and existing sampler controls resolve through tier-aware Qwen3.5 profiles, request overrides and validation, reach the native sampler, appear in privacy-safe effective telemetry and are exposed in the Playground.

Owner: [`workstreams/generation-thinking.md`](workstreams/generation-thinking.md)

## Q35-4 — Generation guard

State: **DONE**

A bounded runtime guard now covers repetition/runaway behavior and optional thinking budget. Guard termination has typed stop reasons distinct from cancellation/max-token/backend failure, preserves correct streaming cleanup, records privacy-safe terminal telemetry and has deterministic runtime tests.

Owner: [`workstreams/generation-thinking.md`](workstreams/generation-thinking.md)

## Q35-5 — Runtime, context and cache capabilities

State: **DONE**

Qwen3.5 runtime capability is bound to the pinned backend revision. Mobile context is restricted to approved 1K/2K/4K/8K tiers with a safety reserve and smallest-fitting Auto selection. Prefix/session/snapshot/reuse optimizations fail closed because hybrid/recurrent state reuse has not been proved safe.

Owner: [`workstreams/runtime-tuning.md`](workstreams/runtime-tuning.md)

## Q35-6 — Android runtime tuning

State: **IN PROGRESS**

Repository-side tuning infrastructure is complete: separate 0.8B/2B candidate profiles, controlled context/thread/batch/ubatch/thinking matrix, strict benchmark execution identity, Room persistence, repeatable physical-device instrumentation, evidence schema v2 and selection-safe aggregation are implemented.

Remaining exit gate:

- run the full matrix on representative physical Android hardware for both reference Q4_K_M artifacts;
- choose separate versioned defaults from eligible TTFT, prefill/decode throughput, memory and thermal evidence;
- prove cancellation, model switch, memory pressure and idle unload on the measured configurations;
- promote profiles to `MEASURED` only after evidence review.

Owner: [`workstreams/runtime-tuning.md`](workstreams/runtime-tuning.md)

## Q35-7 — Qwen3.5 validation suite

State: **PLANNED**

Goal: complete semantic, integration and physical-device evidence before certification.

Owner: [`workstreams/validation-certification.md`](workstreams/validation-certification.md)

## Q35-8 — Certification

State: **PLANNED**

Goal: attach reproducible certification to exact artifact SHA-256, quantization, backend revision and supported device/runtime envelope. Catalog availability remains separate from certification.

Owner: [`workstreams/validation-certification.md`](workstreams/validation-certification.md)
