# Qwen3.5 generation, thinking and guard policy

Status: active
Document type: feature-specification
Owner: qwen35
Canonical scope: qwen35.generation
Read when: implementing Qwen3.5 thinking mode, chat-template arguments, sampling defaults, generation overrides or anomalous-generation guards
Last reviewed: 2026-08-08

## Goal

Make Qwen3.5 generation semantics deterministic and centrally owned so consumer applications do not need Qwen-specific prompt hacks or sampler knowledge.

## Thinking mode

Public/profile intent is model-family-neutral:

```kotlin
enum class ThinkingMode {
    ENABLED,
    DISABLED,
}
```

The internal Qwen3.5 path maps this to the pinned llama.cpp Jinja renderer through typed `enable_thinking` kwargs. No Qwen3-style `/think` or `/nothink` prompt switch is injected.

## Effective configuration precedence

```text
Qwen3.5 tier baseline
  -> use-case preset
  -> request override
  -> validation
  -> effective generation configuration
```

## Sampling fields

The implemented path carries `temperature`, `topP`, `topK`, `minP`, `presencePenalty`, `repeatPenalty`, repeat window, seed policy and maximum output tokens through domain configuration, resolution, native sampler mapping and privacy-safe telemetry.

## Built-in profiles

- `QWEN35_TEXT_FAST`;
- `QWEN35_TEXT_QUALITY`;
- `QWEN35_THINKING`;
- `QWEN35_PRECISE`;
- `QWEN35_JSON`.

Qwen upstream baselines remain evidence inputs rather than immutable constants. The product resolves separate 0.8B/2B tiers without branching consumer code.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| Q35-GEN-01 | DONE | Neutral `ThinkingMode` intent and internal Qwen3.5 translation are implemented. |
| Q35-GEN-02 | DONE | Typed chat-template kwargs reach llama.cpp Jinja `enable_thinking`. |
| Q35-GEN-03 | DONE | `minP` is carried through generation resolution and native sampler mapping. |
| Q35-GEN-04 | DONE | `presencePenalty` is carried through generation resolution and native sampler mapping. |
| Q35-GEN-05 | DONE | Versioned 0.8B/2B Qwen3.5 generation profiles are implemented. |
| Q35-GEN-06 | DONE | Generation overrides are validated against supported scalar bounds and deterministic precedence. |
| Q35-GEN-07 | DONE | Effective thinking mode and scalar sampler configuration are recorded in privacy-safe telemetry. |
| Q35-GEN-08 | PLANNED | Implement bounded anomalous-generation guard execution in `core/runtime-core`. |
| Q35-GEN-09 | PLANNED | Add typed guard stop reasons and lifecycle mapping. |
| Q35-GEN-10 | IN PROGRESS | Template/sampler golden coverage is implemented; guard-specific coverage completes with Q35-4. |

## Q35-3 acceptance

Q35-3 is complete because:

- thinking enabled/disabled resolves deterministically through typed template kwargs;
- no `/think` or `/nothink` injection is required;
- `minP` and `presencePenalty` reach the backend and safe effective configuration;
- request/preset/default precedence is deterministic;
- 0.8B and 2B use typed tier-aware profiles;
- Playground exposes thinking and the sampler controls;
- native sampler, resolver, telemetry/Room, phone unit and instrumentation compilation tests pass;
- repository Validate and Android Package gates are green.

## Q35-4 generation guard

The 0.8B/2B model guidance warns that thinking can loop. Q35-4 therefore remains responsible for bounded repetition/runaway detection, optional thinking budgets, typed guard stop reasons, streaming cleanup and guard-specific tests. It must not duplicate JSON/schema validation already owned by the output layer.

Q35-3 does **not** claim these guard capabilities.

## Upstream references

- https://huggingface.co/Qwen/Qwen3.5-0.8B/blob/main/README.md
- https://huggingface.co/Qwen/Qwen3.5-2B/blob/main/README.md
