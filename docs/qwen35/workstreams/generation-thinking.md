# Qwen3.5 generation, thinking and guard policy

Status: active
Document type: feature-specification
Owner: qwen35
Canonical scope: qwen35.generation
Read when: implementing Qwen3.5 thinking mode, chat-template arguments, sampling defaults, generation overrides or anomalous-generation guards
Last reviewed: 2026-09-06

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

The product resolves separate 0.8B, 2B and 4B tiers without branching consumer code. Existing 0.8B/2B profiles remain unchanged. The 4B tier is versioned with explicit Unsloth-derived sampling values under ADR 0019.

### Qwen3.5 4B mapping

Unsloth documents distinct Qwen3.5 sampler baselines for non-thinking general use, non-thinking reasoning, thinking/general use and lower-temperature precise/coding use. Harnex maps those semantics to its existing preset vocabulary:

| Harnex profile | Unsloth intent | Thinking | temperature | top_p | top_k | min_p | presence_penalty | repeat_penalty |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `QWEN35_TEXT_FAST` | non-thinking general | disabled | 0.7 | 0.8 | 20 | 0 | 1.5 | 1.0 |
| `QWEN35_TEXT_QUALITY` | non-thinking reasoning | disabled | 1.0 | 0.95 | 20 | 0 | 1.5 | 1.0 |
| `QWEN35_THINKING` | thinking general | enabled | 1.0 | 0.95 | 20 | 0 | 1.5 | 1.0 |
| `QWEN35_PRECISE` | thinking precise/coding | enabled | 0.6 | 0.95 | 20 | 0 | 0 | 1.0 |
| `QWEN35_JSON` | non-thinking general | disabled | 0.7 | 0.8 | 20 | 0 | 1.5 | 1.0 |

Unsloth does not define a separate JSON sampler. `QWEN35_JSON` therefore uses its non-thinking general baseline while Harnex's output-mode/schema layer continues to own JSON validity and structure.

The 4B model itself has thinking disabled by default upstream. Harnex does not rely on that implicit default: every built-in profile resolves an explicit `ThinkingMode`, which is translated to `enable_thinking` in the chat template.

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
| Q35-GEN-08 | DONE | Bounded anomalous-generation guard execution is implemented in `core/runtime-core`. |
| Q35-GEN-09 | DONE | Typed guard stop reasons and lifecycle mapping distinguish guard termination from cancellation/backend failure. |
| Q35-GEN-10 | DONE | Template/sampler and guard-specific deterministic coverage pass. |
| Q35-GEN-11 | IN PROGRESS | Add the distinct 4B tier with exact Unsloth sampler mapping while preserving established 0.8B/2B behavior. |

## Acceptance

Established Q35-3/Q35-4 behavior remains valid because:

- thinking enabled/disabled resolves deterministically through typed template kwargs;
- no `/think` or `/nothink` injection is required;
- `minP` and `presencePenalty` reach the backend and safe effective configuration;
- request/preset/default precedence is deterministic;
- 0.8B, 2B and 4B use typed tier-aware profiles;
- selecting another model tier reapplies the active built-in preset so stale lower-tier values do not silently override the selected tier;
- manual overrides remain explicit custom configuration and are not overwritten by model selection;
- Playground exposes thinking and the sampler controls;
- the generation guard remains bounded and independent of the 4B sampling-policy extension.

The 4B addition is not considered production-certified until repository deterministic gates pass on the exact change HEAD and representative physical-device evidence exists for the exact 4B artifact being certified.

## Q35-4 generation guard

The guard owns bounded generation protection without changing template/sampler semantics:

- a versioned policy defines repetition/runaway thresholds and the optional thinking budget;
- detection is bounded and independent of streamed chunk boundaries;
- the backend callback stops decode when the guard fires;
- the public terminal preserves a typed guard stop reason instead of reporting a false user cancellation;
- guard stop telemetry remains privacy-safe;
- deterministic runtime tests cover thinking-budget and repetition termination plus normal generation behavior.

The initial B4 guard budget remains conservative and equal to the current B2 bound until physical-device evidence justifies a wider budget. The guard does not duplicate JSON/schema validation owned by the output layer.

## Upstream references

- https://unsloth.ai/docs/models/qwen3.5
- https://huggingface.co/unsloth/Qwen3.5-4B-GGUF
- https://huggingface.co/Qwen/Qwen3.5-0.8B/blob/main/README.md
- https://huggingface.co/Qwen/Qwen3.5-2B/blob/main/README.md
