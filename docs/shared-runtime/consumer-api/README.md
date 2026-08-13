# Public Consumer API plan

Status: active
Document type: feature-index
Owner: shared-runtime-consumer-api
Canonical scope: shared-runtime.consumer-api.routing
Read when: designing or implementing the application-facing shared-runtime inference API
Last reviewed: 2026-08-13

This is the progressive-disclosure entry point for the API exposed by Local LLM Harness to a separately installed Android application. It owns routing only. Integrated repository state remains in [`../../current-state.md`](../../current-state.md); the cross-application deployment boundary remains in [`../target.md`](../target.md).

## Product rule

The consumer application declares **what it needs**. Harness owns **how local inference is executed**.

A consumer may choose only host-advertised, use-case-authorized options. It never receives filesystem paths, native handles or unrestricted llama.cpp tuning controls.

```text
consumer app
  -> discover authorized capabilities
  -> choose use case + allowed model + allowed preset
  -> submit bounded input and output requirement
  -> receive typed reasoning/answer stream when supported
  -> receive final answer plus stable public metrics

Harness
  -> authenticate caller
  -> resolve exact artifact and effective configuration
  -> prepare/load/schedule/tokenize/template/run/cancel/cleanup
  -> measure and return stable public result metadata
  -> retain deeper operational diagnostics internally
```

## Why this plan exists

The current shared-runtime target intentionally gives the client only an allowlisted `UseCaseId` while the host owns exact model binding. The core contracts, however, already support versioned presets, reasoning-versus-answer content, output constraints and rich generation metrics. Before widening the consumer surface, the repository needs one explicit product boundary for what clients may select and what must remain host-owned.

This plan proposes a constrained consumer API rather than exposing raw generation knobs. It does **not** change the accepted shared-runtime trust model by itself. Any durable change from host-only model binding requires the decision work in the roadmap and, where appropriate, an ADR update.

## Read only what you need

| Question | Canonical source |
| --- | --- |
| What belongs in Harness versus the calling app? | [`target.md`](target.md) |
| What should the public Kotlin/Binder-facing inference surface look like? | [`public-surface-v1.md`](public-surface-v1.md) |
| How are model, preset, reasoning and output capabilities discovered and constrained? | [`capabilities-and-policy.md`](capabilities-and-policy.md) |
| How are reasoning, answer, streaming and public metrics represented? | [`results-and-metrics.md`](results-and-metrics.md) |
| How do we prove the boundary, compatibility, security and consumer usability? | [`validation-and-rollout.md`](validation-and-rollout.md) |
| In what order should this be implemented and what closes each milestone? | [`roadmap.md`](roadmap.md) |
| How does the first product-shaped PDF/PII reference consumer work? | [`pii-redactor/README.md`](pii-redactor/README.md) |

Do not read every workstream for a focused change. For example, a metrics-only change should read the result contract plus the relevant existing core metrics source, not the entire capability-policy plan.

The nested PII plan owns document extraction, PII definitions, prompt payload, result validation, review, redaction, export and OMBRA presentation. Those application details do not belong in the generic SDK, Binder protocol or Harness runtime.

## Proposed v1 at a glance

The public consumer surface should expose:

- authenticated application identity derived from the Binder caller;
- explicit `UseCaseId`;
- discovery of host-authorized logical model choices and versioned inference presets;
- host-provided defaults so the client may omit model/preset selection;
- bounded text or message input and supported output constraints;
- an explicit reasoning preference only when the resolved model/use case supports a surfaced reasoning channel;
- ordered lifecycle and content events;
- final answer, optional surfaced reasoning output and stable public performance metrics;
- cancellation, close and typed failures.

It should not expose:

- GGUF URLs, paths or direct artifact installation controls;
- arbitrary temperature/top-p/top-k/min-p/repetition/thread/batch/context tuning to ordinary consumers;
- chat-template, tokenizer, native backend or scheduler internals;
- host-wide logs, health, memory, thermal, cache or benchmark control;
- private telemetry databases or backend-specific errors.

## Public versus diagnostic information

Use three disclosure levels:

1. **Consumer essentials** — answer, optional surfaced reasoning, TTFT, total time, output tokens and decode speed.
2. **Request details** — input/reasoning/answer token counts, stop reason, effective logical model and preset, protocol/request identifiers.
3. **Harness diagnostics** — queue/model-load/prefill/decode/context timing, memory, thermal, scheduler, cache/native and global operational data.

Only levels 1 and the privacy-safe subset of level 2 belong in the normal consumer contract. Level 3 remains Harness-owned unless a separate diagnostics protocol explicitly authorizes it.

## Relationship to existing contracts

Preserve and reuse the existing backend-neutral concepts wherever possible:

- `LocalLlmClient` lifecycle;
- `ApplicationId + UseCaseId` binding;
- `InferencePresetRef` versioning;
- `GenerationContentType.REASONING` and `ANSWER`;
- `OutputConstraint`;
- `GenerationMetrics` as the internal superset from which a stable consumer metric projection can be derived.

Do not make Binder parcel DTOs the public source of truth. The supported consumer SDK remains a high-level Kotlin surface and the Android wire representation maps at the transport edge.

## Documentation ownership rule

This directory owns the intended **application-facing inference contract**. Existing shared-runtime documents continue to own trust, Binder lifecycle, host/client process ownership and release evidence. Existing model/generation documents continue to own model internals and runtime tuning.

When a proposed behavior becomes accepted and implemented, update the focused owner here and only the higher-level routing/state documents whose summary actually changed.
