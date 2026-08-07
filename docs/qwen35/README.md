# Qwen3.5 specialization

Status: active
Document type: feature-index
Owner: qwen35
Canonical scope: qwen35.routing
Read when: locating the current Qwen3.5 specialization status, next implementation gate or owning specification
Last reviewed: 2026-08-07

This is the single entry point for the Qwen3.5-only specialization work. Start here, then open only the document that owns the question.

## Active support target

The active target is deliberately narrow:

- Qwen3.5 dense `0.8B` and `2B` only;
- GGUF inference through the pinned `llama.cpp` backend;
- Android `arm64-v8a`, CPU-first;
- text generation only;
- thinking and non-thinking modes;
- streaming, cancellation and existing `TEXT`, `JSON` and `JSON_SCHEMA` output constraints.

Qwen3.5 `4B`, `9B`, MoE variants, vision, tool calling, Vulkan/GPU and speculative/MTP execution are not part of this workstream.

## Progress at a glance

| Milestone | State | Meaning |
| --- | --- | --- |
| Q35-0 Scope and plan | DONE | This progressive-disclosure plan is canonical and linked from the documentation map. |
| Q35-1 Compatibility gate | PLANNED | Detect and accept only supported dense Qwen3.5 artifacts and validate the backend pin. |
| Q35-2 Thinking and sampling | PLANNED | Make Qwen3.5 template semantics and sampling first-class. |
| Q35-3 Generation guard | PLANNED | Add bounded detection and typed stop reasons for anomalous generation. |
| Q35-4 Runtime/context/cache capabilities | PLANNED | Make hybrid/recurrent-state assumptions explicit and safe. |
| Q35-5 Android tuning | PLANNED | Establish separate 0.8B and 2B runtime profiles from device evidence. |
| Q35-6 Validation | PLANNED | Add golden, integration and physical-device Qwen3.5 validation. |
| Q35-7 Certification/catalog | PLANNED | Publish only evidence-backed artifact/quantization combinations as certified. |

The next implementation gate is **Q35-1 Compatibility gate**.

## What to read

| Need | Read |
| --- | --- |
| Current progress, blockers and immediate next tasks | [`current-state.md`](current-state.md) |
| Product objective, support envelope and non-goals | [`target.md`](target.md) |
| Runtime/module design and ownership boundaries | [`architecture.md`](architecture.md) |
| Milestone order, dependencies and exit gates | [`roadmap.md`](roadmap.md) |
| Detection, backend compatibility and typed rejection | [`workstreams/model-compatibility.md`](workstreams/model-compatibility.md) |
| Thinking, chat-template kwargs, sampling and generation guard | [`workstreams/generation-thinking.md`](workstreams/generation-thinking.md) |
| Context, recurrent state, cache policy and Android tuning | [`workstreams/runtime-tuning.md`](workstreams/runtime-tuning.md) |
| Golden tests, device evidence and certification | [`workstreams/validation-certification.md`](workstreams/validation-certification.md) |

## Ownership rule

These documents own only the Qwen3.5 specialization delta. Existing generic behavior remains owned by the repository sources linked below; do not duplicate their contracts here.

- generation baseline: [`../generation-configuration-and-prompting-plan.md`](../generation-configuration-and-prompting-plan.md)
- repository state: [`../current-state.md`](../current-state.md)
- architecture: [`../architecture.md`](../architecture.md)
- model catalog/distribution: [`../model-catalog-download-plan.md`](../model-catalog-download-plan.md)
- benchmark behavior: [`../benchmark-engine.md`](../benchmark-engine.md)
- merge/release completion: [`../definition-of-done.md`](../definition-of-done.md)
