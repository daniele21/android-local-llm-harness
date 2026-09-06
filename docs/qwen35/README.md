# Qwen3.5-only product plan

Status: active
Document type: feature-index
Owner: qwen35
Canonical scope: qwen35.routing
Read when: locating the current Qwen3.5-only product status, next implementation gate or owning specification
Last reviewed: 2026-09-06

This is the single entry point for the Qwen3.5-only product work. Start here, then open only the document that owns the question.

## Decision summary

The product supports and tunes only:

- Qwen3.5 dense `0.8B` and `2B` curated releases;
- Qwen3.5 dense `4B` from the reviewed Unsloth `Qwen3.5-4B-GGUF` source, restricted to the seven admitted 4-bit variants;
- GGUF inference through the pinned `llama.cpp` backend;
- Android `arm64-v8a`, CPU-first;
- text generation only;
- thinking and non-thinking modes;
- streaming, cancellation and existing `TEXT`, `JSON` and `JSON_SCHEMA` output constraints.

The product model surface is closed. Users choose only repository-reviewed catalog entries and cannot import arbitrary GGUF models. There is no legacy-model compatibility layer or generic family fallback. Core lifecycle/backend contracts remain family-neutral without becoming a product extension point. [ADR 0011](../adr/0011-qwen35-only-product-support.md) owns the Qwen3.5-only boundary and [ADR 0019](../adr/0019-qwen35-4b-four-bit-product-support.md) extends it with the 4B 4-bit tier.

Certification remains evidence-based per exact artifact. Admitting the 4B tier does not certify it; all 4B entries remain `CANDIDATE` until exact-artifact physical-device evidence passes.

## Progress at a glance

| Milestone | State | Meaning |
| --- | --- | --- |
| Q35-0 Decision and plan | DONE | Qwen3.5-only envelope is canonical; ADR 0019 records the explicit 4B 4-bit extension. |
| Q35-1 Curated model baseline | IN PROGRESS | Established 0.8B/2B closed catalog is complete; exact Unsloth 4B 4-bit catalog extension is under validation. |
| Q35-2 Compatibility gate | DONE | Exact 0.8B/2B Q4_K_M artifacts pass trusted GGUF and pinned-backend compatibility proof; 4B does not inherit that certification. |
| Q35-3 Thinking and sampling | IN PROGRESS | Established semantics remain intact; B4 adds Unsloth-derived tier-aware sampling and tier-selection coverage. |
| Q35-4 Generation guard | DONE | Bounded runaway/repetition protection and typed guard stops are implemented and tested. |
| Q35-5 Runtime/context/cache capabilities | DONE | Backend-revision-bound context/reuse policy is conservative and fail-closed; B4 starts as an unmeasured candidate. |
| Q35-6 Android tuning | IN PROGRESS | Matrix, strict benchmark identity and evidence tooling are ready; physical reference-tier measurements remain. |
| Q35-7 Validation | PLANNED | Complete golden, integration and physical-device Qwen3.5 validation, including the exact 4B candidate chosen for certification. |
| Q35-8 Certification | PLANNED | Attach evidence-backed certification to exact curated artifacts. |

The immediate repository gate for the 4B extension is deterministic exact-HEAD validation of the catalog, generation profiles and connected phone surface. Representative physical Android evidence is a separate certification gate.

## What to read

| Need | Read |
| --- | --- |
| Current progress, blockers and immediate next tasks | [`current-state.md`](current-state.md) |
| Product objective, support envelope and non-goals | [`target.md`](target.md) |
| Runtime/module design and ownership boundaries | [`architecture.md`](architecture.md) |
| Milestone order, dependencies and exit gates | [`roadmap.md`](roadmap.md) |
| Closed catalog and removal of generic/import paths | [`workstreams/curated-model-baseline.md`](workstreams/curated-model-baseline.md) |
| Exact artifact and backend compatibility | [`workstreams/model-compatibility.md`](workstreams/model-compatibility.md) |
| Thinking, chat-template kwargs, sampling and generation guard | [`workstreams/generation-thinking.md`](workstreams/generation-thinking.md) |
| Reasoning/answer stream separation and controlled close behavior | [`../qwen-reasoning-output-separation.md`](../qwen-reasoning-output-separation.md) |
| Context, recurrent state, cache policy and Android tuning | [`workstreams/runtime-tuning.md`](workstreams/runtime-tuning.md) |
| Golden tests, device evidence and certification | [`workstreams/validation-certification.md`](workstreams/validation-certification.md) |

## Ownership rule

These documents own only the Qwen3.5 product delta. Generic execution mechanics remain with their repository owners; Qwen documents specify policy inputs and acceptance rather than parallel implementations.
