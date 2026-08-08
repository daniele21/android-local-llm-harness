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
Q35-0 Decision/plan
   |
   v
Q35-1 Curated model baseline
   |
   v
Q35-2 Compatibility gate
   |
   v
Q35-3 Thinking + sampling
   |
   v
Q35-4 Generation guard
   |
   v
Q35-5 Runtime/context/cache capabilities
   |
   v
Q35-6 Android tuning
   |
   v
Q35-7 Validation
   |
   v
Q35-8 Certification
```

Q35-1 closes the product model surface before model-specific execution work begins. The product does not preserve arbitrary import or retired-family compatibility. Q35-3 and parts of Q35-5 may be developed in parallel only after Q35-2 proves the exact curated artifact/backend boundary. Certification consumes Q35-6 and Q35-7 evidence.

## Q35-0 — Decision and planning

State: **DONE**

Exit gate:

- ADR 0011 makes the 0.8B/2B dense-only product target canonical;
- non-goals are explicit;
- progressive-disclosure routing exists;
- workstream owners and acceptance gates are documented.

## Q35-1 — Curated model baseline

State: **IN PROGRESS**

Goal: make the product expose only the reviewed Qwen3.5 dense 0.8B/2B model set and delete generic model-import/product paths that are no longer needed.

Implemented on `dev`:

- executable catalog restricted to the seven reviewed Qwen3.5 dense 0.8B/2B releases;
- consumer-facing manual GGUF import removed from Android UI, effects and controller;
- product runtime binding anchored to exact curated release metadata and release profile identity;
- former imported/SAF product provenance removed from the consumer runtime path;
- product fixtures migrated away from retired model families while generic lifecycle contracts remain family-neutral;
- external-import inventory origin/projection removed;
- out-of-catalog selections and persisted metadata are ignored rather than surfaced through legacy/unsupported states;
- Models, Overview and Playground use the curated catalog path;
- developer-only artifact injection remains isolated in validation tooling.

Current gate: complete applicable Android/package validation and final closed-surface assertions before marking Q35-1 done.

Exit gate:

- the executable catalog contains only Qwen3.5 dense 0.8B/2B releases;
- consumer-facing manual GGUF import is removed;
- product bindings and profiles reference only curated releases;
- Models and Playground expose only curated downloadable/installed Qwen3.5 models;
- no legacy/unsupported model state is introduced to preserve retired cases;
- verified catalog download/install remains the only product acquisition path;
- isolated developer test artifact injection remains outside product APIs;
- deterministic catalog, binding, inventory and UI tests pass;
- applicable Android/package repository validation is green for the implementation head.

Owner: [`workstreams/curated-model-baseline.md`](workstreams/curated-model-baseline.md)

## Q35-2 — Model/backend compatibility

State: **PLANNED**

Goal: prove the exact curated Qwen3.5 artifacts against the repository-pinned backend before relying on them for product inference.

Exit gate:

- actual pinned `llama.cpp` revision is inspected and recorded;
- selected 0.8B and 2B reference GGUFs complete load/tokenize/minimal-generate smoke tests;
- GGUF metadata for each curated artifact matches the trusted catalog manifest;
- exact artifact digest and backend revision are carried into compatibility evidence;
- corrupted, mismatched or backend-incompatible curated artifacts fail before runtime preparation;
- deterministic compatibility integration tests pass.

Owner: [`workstreams/model-compatibility.md`](workstreams/model-compatibility.md)

## Q35-3 — Thinking, template and sampling

State: **PLANNED**

Goal: make Qwen3.5 generation semantics a first-class deterministic plan.

Exit gate:

- request/use-case policy exposes model-family-neutral thinking enabled/disabled intent;
- template application passes Qwen3.5 `enable_thinking` semantics without prompt hacks;
- `presencePenalty` and `minP` are representable end-to-end;
- Qwen3.5 text presets resolve through one precedence chain;
- effective generation telemetry records safe scalar configuration;
- golden template and sampler-resolution tests pass.

Owner: [`workstreams/generation-thinking.md`](workstreams/generation-thinking.md)

## Q35-4 — Generation guard

State: **PLANNED**

Goal: bound known small-model failure modes without hiding normal cancellation or backend failures.

Exit gate:

- repetition/runaway detection is bounded and deterministic;
- thinking budget policy exists where enabled;
- guard termination has typed, telemetry-safe stop reasons;
- guard works in streaming execution and is tested independently of UI;
- JSON/output-constraint failures remain owned by the existing output layer rather than duplicated.

Owner: [`workstreams/generation-thinking.md`](workstreams/generation-thinking.md)

## Q35-5 — Runtime, context and cache capabilities

State: **PLANNED**

Goal: make mobile memory policy safe for Qwen3.5 hybrid/recurrent execution.

Exit gate:

- approved mobile context tiers replace blind model-max allocation;
- runtime capabilities explicitly distinguish ordinary context lifecycle from snapshot/restore/prefix reuse;
- unsupported cache/reuse optimization is disabled or rejected;
- backend revision is part of capability evidence;
- memory-pressure and cancellation cleanup remain correct.

Owner: [`workstreams/runtime-tuning.md`](workstreams/runtime-tuning.md)

## Q35-6 — Android runtime tuning

State: **PLANNED**

Goal: produce separate evidence-backed CPU tuning profiles for 0.8B and 2B.

Exit gate:

- benchmark key includes exact artifact, quantization, backend revision and device/runtime configuration;
- candidate thread/batch/ubatch/context settings are measured on representative hardware;
- 0.8B and 2B are tuned independently;
- chosen defaults satisfy memory, thermal and responsiveness bounds;
- tuning remains overrideable for diagnostics without changing certified defaults.

Owner: [`workstreams/runtime-tuning.md`](workstreams/runtime-tuning.md)

## Q35-7 — Qwen3.5 validation suite

State: **PLANNED**

Goal: prove semantic correctness before certification.

Exit gate:

- tokenizer/special-token/UTF-8 golden tests pass;
- thinking on/off templates pass;
- sampling-resolution tests pass;
- streaming, aggregate completion and cancellation paths pass;
- `TEXT`, `JSON` and `JSON_SCHEMA` paths pass;
- generation guard failure cases pass;
- physical-device load/generate/memory/thermal evidence exists for the certification candidates.

Owner: [`workstreams/validation-certification.md`](workstreams/validation-certification.md)

## Q35-8 — Certification

State: **PLANNED**

Goal: attach reproducible, evidence-backed certification to exact curated Qwen3.5 combinations.

Exit gate:

- certification matrix keys exact artifact SHA-256, quantization and validated backend revision;
- supported device/runtime envelope is recorded;
- catalog availability remains separate from `CERTIFIED`, `TESTED` and `EXPERIMENTAL` evidence status;
- initial certification is limited to the exact 0.8B Q4_K_M and 2B Q4_K_M candidates;
- additional curated quantizations require their own evidence identity and cannot inherit certification;
- release documentation contains physical-device evidence references without model binaries or private paths.

Owner: [`workstreams/validation-certification.md`](workstreams/validation-certification.md)
