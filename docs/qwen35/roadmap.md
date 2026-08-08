# Qwen3.5-only product roadmap

Status: active
Document type: roadmap
Owner: qwen35
Canonical scope: qwen35.roadmap
Read when: selecting the next Qwen3.5 milestone, checking dependencies or deciding whether a later capability can start
Last reviewed: 2026-08-08

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
Q35-4 Generation guard            NEXT
   |
Q35-5 Runtime/context/cache capabilities
   |
Q35-6 Android tuning
   |
Q35-7 Validation
   |
Q35-8 Certification
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

Neutral thinking intent maps to typed Jinja `enable_thinking`; no prompt soft-switch is injected. `minP`, `presencePenalty` and existing sampler controls resolve through tier-aware Qwen3.5 profiles, request overrides and validation, reach the native sampler, appear in privacy-safe effective telemetry and are exposed in the Playground. Native/Android/package gates pass including `libllama-common.so` packaging.

Owner: [`workstreams/generation-thinking.md`](workstreams/generation-thinking.md)

## Q35-4 — Generation guard

State: **PLANNED**

Goal: bound known small-model runaway/repetition modes without hiding normal cancellation or backend failures.

Exit gate:

- versioned bounded repetition/runaway detection;
- optional thinking budget policy;
- typed guard stop reasons distinct from cancellation, max tokens and native failure;
- correct streaming cleanup after guard termination;
- privacy-safe stop telemetry;
- deterministic guard tests independent of the UI.

Owner: [`workstreams/generation-thinking.md`](workstreams/generation-thinking.md)

## Q35-5 — Runtime, context and cache capabilities

State: **PLANNED**

Goal: make mobile memory policy safe for Qwen3.5 hybrid/recurrent execution and gate snapshot/prefix reuse by verified backend capability.

Owner: [`workstreams/runtime-tuning.md`](workstreams/runtime-tuning.md)

## Q35-6 — Android runtime tuning

State: **PLANNED**

Goal: produce separate evidence-backed CPU tuning profiles for 0.8B and 2B on representative hardware.

Owner: [`workstreams/runtime-tuning.md`](workstreams/runtime-tuning.md)

## Q35-7 — Qwen3.5 validation suite

State: **PLANNED**

Goal: complete semantic, integration and physical-device evidence before certification.

Owner: [`workstreams/validation-certification.md`](workstreams/validation-certification.md)

## Q35-8 — Certification

State: **PLANNED**

Goal: attach reproducible certification to exact artifact SHA-256, quantization, backend revision and supported device/runtime envelope. Catalog availability remains separate from certification.

Owner: [`workstreams/validation-certification.md`](workstreams/validation-certification.md)
