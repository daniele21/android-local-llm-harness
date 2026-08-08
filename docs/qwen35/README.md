# Qwen3.5-only product plan

Status: active
Document type: feature-index
Owner: qwen35
Canonical scope: qwen35.routing
Read when: locating the current Qwen3.5-only product status, next implementation gate or owning specification
Last reviewed: 2026-08-08

This is the single entry point for the Qwen3.5-only product work. Start here, then open only the document that owns the question.

## Decision summary

The product will support, tune and certify only:

- Qwen3.5 dense `0.8B` and `2B`;
- GGUF inference through the pinned `llama.cpp` backend;
- Android `arm64-v8a`, CPU-first;
- text generation only;
- thinking and non-thinking modes;
- streaming, cancellation and existing `TEXT`, `JSON` and `JSON_SCHEMA` output constraints.

The product model surface is closed. Users choose only from repository-reviewed catalog entries and cannot import arbitrary GGUF models. There is no legacy-model compatibility layer or generic family fallback.

Core lifecycle and backend contracts remain model-family neutral. That neutrality preserves module boundaries; it is not a product extension point. [ADR 0011](../adr/0011-qwen35-only-product-support.md) owns this repository-level decision.

## Progress at a glance

| Milestone | State | Meaning |
| --- | --- | --- |
| Q35-0 Decision and plan | DONE | The support envelope, decision record and progressive-disclosure routing are canonical. |
| Q35-1 Curated model baseline | IN PROGRESS | Closed catalog/import removal plus catalog-only binding, persistence and inventory cleanup are implemented; applicable Android/package validation still gates completion. |
| Q35-2 Compatibility gate | PLANNED | Prove the exact curated artifacts against the pinned backend. |
| Q35-3 Thinking and sampling | PLANNED | Make Qwen3.5 template semantics and sampling first-class behind neutral intent. |
| Q35-4 Generation guard | PLANNED | Add bounded detection and typed stop reasons for anomalous generation. |
| Q35-5 Runtime/context/cache capabilities | PLANNED | Make hybrid/recurrent-state assumptions explicit and safe. |
| Q35-6 Android tuning | PLANNED | Establish separate 0.8B and 2B runtime profiles from device evidence. |
| Q35-7 Validation | PLANNED | Add golden, integration and physical-device Qwen3.5 validation. |
| Q35-8 Certification | PLANNED | Attach evidence-backed certification to exact curated artifacts. |

The current implementation gate is **Q35-1 validation closure**. The product-side closed Qwen3.5 surface is implemented, but Q35-1 remains `IN PROGRESS` until its applicable repository/Android/package checks pass. Q35-2 starts only after that gate closes.

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
| Context, recurrent state, cache policy and Android tuning | [`workstreams/runtime-tuning.md`](workstreams/runtime-tuning.md) |
| Golden tests, device evidence and certification | [`workstreams/validation-certification.md`](workstreams/validation-certification.md) |

## Ownership rule

These documents own only the Qwen3.5 product delta. Generic execution mechanics remain with their repository owners; Qwen documents specify policy inputs and acceptance, not parallel implementations.

- generation baseline: [`../generation-configuration-and-prompting-plan.md`](../generation-configuration-and-prompting-plan.md)
- repository state: [`../current-state.md`](../current-state.md)
- architecture: [`../architecture.md`](../architecture.md)
- model catalog/distribution: [`../model-catalog-download-plan.md`](../model-catalog-download-plan.md)
- benchmark behavior: [`../benchmark-engine.md`](../benchmark-engine.md)
- merge/release completion: [`../definition-of-done.md`](../definition-of-done.md)
