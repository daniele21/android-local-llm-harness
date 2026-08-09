# Qwen3.5-only product plan

Status: active
Document type: feature-index
Owner: qwen35
Canonical scope: qwen35.routing
Read when: locating the current Qwen3.5-only product status, next implementation gate or owning specification
Last reviewed: 2026-08-09

This is the single entry point for the Qwen3.5-only product work. Start here, then open only the document that owns the question.

## Decision summary

The product supports, tunes and will certify only:

- Qwen3.5 dense `0.8B` and `2B`;
- GGUF inference through the pinned `llama.cpp` backend;
- Android `arm64-v8a`, CPU-first;
- text generation only;
- thinking and non-thinking modes;
- streaming, cancellation and existing `TEXT`, `JSON` and `JSON_SCHEMA` output constraints.

The product model surface is closed. Users choose only repository-reviewed catalog entries and cannot import arbitrary GGUF models. There is no legacy-model compatibility layer or generic family fallback. Core lifecycle/backend contracts remain family-neutral without becoming a product extension point. [ADR 0011](../adr/0011-qwen35-only-product-support.md) owns that decision.

## Progress at a glance

| Milestone | State | Meaning |
| --- | --- | --- |
| Q35-0 Decision and plan | DONE | Support envelope, ADR and routing are canonical. |
| Q35-1 Curated model baseline | DONE | Closed catalog-only product surface, unified Models lifecycle and validation are complete. |
| Q35-2 Compatibility gate | DONE | Exact 0.8B/2B Q4_K_M artifacts pass trusted GGUF and pinned-backend compatibility proof. |
| Q35-3 Thinking and sampling | DONE | Typed thinking/Jinja semantics and tier-aware sampling resolve end-to-end through Playground, backend and telemetry. |
| Q35-4 Generation guard | DONE | Bounded runaway/repetition protection and typed guard stops are implemented and tested. |
| Q35-5 Runtime/context/cache capabilities | DONE | Backend-revision-bound context/reuse policy is conservative and fail-closed. |
| Q35-6 Android tuning | IN PROGRESS | Matrix, strict benchmark identity and evidence tooling are ready; physical 0.8B/2B measurements remain. |
| Q35-7 Validation | PLANNED | Complete golden, integration and physical-device Qwen3.5 validation. |
| Q35-8 Certification | PLANNED | Attach evidence-backed certification to exact curated artifacts. |

The immediate gate is **Q35-6 physical Android tuning evidence**. Repository-side tuning infrastructure is complete; profiles remain `CANDIDATE` until both reference tiers are measured and reviewed.

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

These documents own only the Qwen3.5 product delta. Generic execution mechanics remain with their repository owners; Qwen documents specify policy inputs and acceptance rather than parallel implementations.
