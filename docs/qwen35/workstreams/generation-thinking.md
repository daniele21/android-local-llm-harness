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

Expose model-family-neutral intent through the public/profile boundary:

```kotlin
enum class ThinkingMode {
    ENABLED,
    DISABLED,
}
```

The internal Qwen3.5 policy translates that intent into the chat-template semantics for `enable_thinking`. `Qwen35ThinkingMode` must not leak into public lifecycle contracts.

Do not implement Qwen3-style `/think` or `/nothink` prompt switches. Qwen's current 0.8B and 2B model cards explicitly state that Qwen3.5 does not officially support that soft switch.

## Effective configuration precedence

Resolve exactly one effective generation configuration:

```text
Qwen3.5 family baseline
  -> exact supported tier baseline
  -> use-case preset
  -> request override
  -> validation
  -> effective generation configuration
```

The existing binding/preset/request architecture remains the owner of generic resolution mechanics.

## Sampling fields

The Qwen3.5 path must represent at least:

- `temperature`;
- `topP`;
- `topK`;
- `minP`;
- `presencePenalty`;
- `repeatPenalty`;
- seed policy;
- maximum output tokens.

`minP` and `presencePenalty` must be wired through public/domain configuration, backend mapping, telemetry-safe effective configuration and deterministic tests.

## Built-in Qwen3.5 profiles

Start with a small typed set rather than arbitrary per-model constants:

- `QWEN35_TEXT_FAST`;
- `QWEN35_TEXT_QUALITY`;
- `QWEN35_THINKING`;
- `QWEN35_PRECISE`;
- `QWEN35_JSON`.

Profile names may change, but each profile must document intended mode/use case and resolve to explicit scalar values.

As of 2026-08-07, Qwen's 2B model card recommends these text baselines:

| Use | temperature | top_p | top_k | min_p | presence_penalty | repetition_penalty |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Non-thinking text | 1.0 | 1.00 | 20 | 0.0 | 2.0 | 1.0 |
| Thinking text | 1.0 | 0.95 | 20 | 0.0 | 1.5 | 1.0 |
| Thinking precise/coding | 0.6 | 0.95 | 20 | 0.0 | 0.0 | 1.0 |

Treat these values as upstream baselines to validate, not permanent constants immune to future Qwen guidance or on-device evidence.

## Generation guard

The 0.8B and 2B model cards warn that thinking mode can enter loops. Qwen3.5 policy therefore supplies versioned thresholds to a bounded guard executed by `core/runtime-core`, which already owns streaming and terminal lifecycle.

Initial responsibilities:

- prefer token-window detection with bounded state so results do not depend on UTF-8 chunk boundaries;
- enforce an optional thinking-generation budget when thinking is enabled;
- stop on a deterministic guard condition without corrupting stream lifecycle;
- emit typed stop reasons distinct from user cancellation, max tokens and backend failure;
- keep guard thresholds versioned with the effective profile;
- avoid persisting generated text in telemetry.

The guard must not duplicate JSON/schema validation already owned by the output-constraint layer.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| Q35-GEN-01 | PLANNED | Add neutral `ThinkingMode` intent and internal Qwen3.5 translation. |
| Q35-GEN-02 | PLANNED | Extend chat-template application with typed kwargs and `enable_thinking`. |
| Q35-GEN-03 | PLANNED | Add `minP` to generation configuration and llama.cpp sampler mapping. |
| Q35-GEN-04 | PLANNED | Add `presencePenalty` to generation configuration and llama.cpp sampler mapping. |
| Q35-GEN-05 | PLANNED | Define versioned 0.8B/2B Qwen3.5 generation profiles from upstream baselines. |
| Q35-GEN-06 | PLANNED | Add validation for incompatible/unsafe override combinations where evidence supports it. |
| Q35-GEN-07 | PLANNED | Record effective thinking mode and scalar sampler configuration in privacy-safe telemetry. |
| Q35-GEN-08 | PLANNED | Implement bounded guard execution in `core/runtime-core` from versioned Qwen3.5 thresholds. |
| Q35-GEN-09 | PLANNED | Add typed guard stop reasons and lifecycle mapping. |
| Q35-GEN-10 | PLANNED | Add golden template, sampler-resolution and guard tests. |

## Acceptance criteria

Q35-3 and Q35-4 are complete when:

- thinking enabled/disabled renders deterministically through template kwargs;
- no `/think` or `/nothink` injection is required;
- `minP` and `presencePenalty` reach the backend and appear in safe effective configuration;
- profile/request precedence is deterministic;
- 0.8B and 2B may have separate profile/guard tuning without branching consumer code;
- guard-triggered completion is distinguishable from cancellation, max-token stop and native failure;
- streaming cleanup remains correct after a guard stop;
- telemetry contains scalar configuration and stop reason but not prompt/output/template text;
- golden and integration tests pass.

## Upstream references

- https://huggingface.co/Qwen/Qwen3.5-0.8B/blob/main/README.md
- https://huggingface.co/Qwen/Qwen3.5-2B/blob/main/README.md
